import { useMemo } from 'react'
import { create } from 'zustand'
import { createJSONStorage, persist } from 'zustand/middleware'
import { ApiError, DEFAULT_ERROR_MESSAGE } from '../../shared/api/apiError'
import { RACE_MS } from '../../shared/components/RaceLoadingBar'
import { withMinDuration } from '../../shared/utils/minDuration'
import type { RecommendRequest } from '../condition/recommendRequest'
import { createRecommendation, regenerateRecommendation } from './api/recommendApi'
import { toCourse, type Course, type RecommendResponse } from './types'

/*
 * [이 파일이 하는 일]
 * 추천 결과(RES-01) 상태. 외출 조건 [추천받기] 가 request 를 부르고, 결과 화면이 이 상태를 그린다.
 *   - result  : 마지막 추천 응답 (RecommendResponse). 코스 상세·지도·교체는 recommendationId 로 서버에서 다시 조회한다
 *   - loading : 추천 계산 중 (공통 LoadingState 표시). 토끼·거북이 달리기가 한 판은 보이도록 최소 RACE_MS(2.1초) 유지
 *   - error   : 실패 (code 로 분기: 410 만료, 429 너무 자주 등)
 * 새로고침해도 결과 화면이 유지되도록 sessionStorage 에 저장한다 (탭을 닫으면 사라짐). 2시간 보관 판단은 서버 expiresAt 기준.
 */
interface RecommendError {
  code: string
  message: string
}

interface RecommendState {
  result: RecommendResponse | null
  loading: boolean
  error: RecommendError | null
  /** 새 조건으로 추천 (CON-01 [추천받기]) */
  request: (body: RecommendRequest) => Promise<void>
  /** 같은 조건으로 다시 추천 (RES-01). 실패하면 오류를 돌려준다 (결과는 그대로 유지) */
  regenerate: () => Promise<RecommendError | null>
}

function toError(e: unknown): RecommendError {
  return e instanceof ApiError ? { code: e.code, message: e.message } : { code: 'UNKNOWN', message: DEFAULT_ERROR_MESSAGE }
}

export const useRecommendStore = create<RecommendState>()(
  persist(
    (set, get) => ({
      result: null,
      loading: false,
      error: null,
      request: async (body) => {
        set({ loading: true, error: null, result: null })
        try {
          set({ result: await withMinDuration(createRecommendation(body), RACE_MS), loading: false })
        } catch (e) {
          set({ error: toError(e), loading: false })
        }
      },
      regenerate: async () => {
        const setId = get().result?.recommendationSetId
        if (!setId) return { code: 'NO_RESULT', message: '추천 결과가 없어요. 조건을 다시 골라 주세요.' }
        set({ loading: true })
        try {
          set({ result: await withMinDuration(regenerateRecommendation(setId), RACE_MS), loading: false, error: null })
          return null
        } catch (e) {
          const error = toError(e)
          // 만료(410)·없음(404)이면 결과 화면을 만료 안내로 바꾸고, 그 밖(429 등)은 결과를 두고 토스트로만 알린다
          if (error.code === 'RECOMMENDATION_EXPIRED' || error.code === 'RECOMMENDATION_NOT_FOUND') set({ error, loading: false })
          else set({ loading: false })
          return error
        }
      },
    }),
    {
      name: 'teumteum-recommend',
      storage: createJSONStorage(() => sessionStorage),
      // 계산 중 상태는 저장하지 않는다 (새로고침 후 무한 로딩 방지)
      partialize: (state) => ({ result: state.result, error: state.error }),
    },
  ),
)

/** 결과 화면용 코스 목록 (응답 → 화면용 Course). 결과가 바뀔 때만 다시 만든다 */
export function useRecommendCourses(): Course[] {
  const result = useRecommendStore((s) => s.result)
  return useMemo(() => (result ? result.courses.map((c) => toCourse(c, result.condition)) : []), [result])
}

/** 서버 오류 코드가 "추천이 만료됐거나 없어진 경우"인지 */
export function isExpiredError(code: string | undefined): boolean {
  return code === 'RECOMMENDATION_EXPIRED' || code === 'RECOMMENDATION_NOT_FOUND'
}
