import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router'
import { ApiError, DEFAULT_ERROR_MESSAGE } from '../../../shared/api/apiError'
import { CATEGORY_STICKER } from '../../../shared/assets/stickerAssets'
import BottomActionBar from '../../../shared/components/BottomActionBar'
import Button from '../../../shared/components/Button'
import EmptyState from '../../../shared/components/EmptyState'
import LoadingState from '../../../shared/components/LoadingState'
import StickerImage from '../../../shared/components/StickerImage'
import Toast from '../../../shared/components/Toast'
import { getCategoryLabel } from '../../../shared/types/domain'
import { formatMinutes } from '../../../shared/utils/format'
import { getAlternatives, replacePlace } from '../api/recommendApi'
import { isExpiredError } from '../store'
import type { AlternativePlaceListResponse } from '../types'
import ExpiredState from './ExpiredState'

interface PlaceReplaceProps {
  courseId: string
  placeId: string
}

type ApiFail = { code: string; message: string }

function toFail(e: unknown): ApiFail {
  return e instanceof ApiError ? { code: e.code, message: e.message } : { code: 'UNKNOWN', message: DEFAULT_ERROR_MESSAGE }
}

/**
 * 장소 교체 (DET-03): 같은 세분류 우선 후보 5곳(GET .../alternatives), 고르면 교체(POST .../replace).
 * - 바꾸면 이용 시간을 넘는 후보는 서버가 거절(409)하므로 '시간 초과' 표시 후 고를 수 없게 한다
 * - 교체 성공 → 응답의 새 recommendationId 상세로 이동 (원래 코스도 만료 전까지 남는다)
 * - 2시간 지남(410)·없음(404) → 만료 안내
 */
export default function PlaceReplace({ courseId, placeId }: PlaceReplaceProps) {
  const navigate = useNavigate()
  const [data, setData] = useState<AlternativePlaceListResponse | null>(null)
  const [loadError, setLoadError] = useState<ApiFail | null>(null)
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [replacing, setReplacing] = useState(false)
  const [toast, setToast] = useState<string | null>(null)
  const closeToast = useCallback(() => setToast(null), [])

  useEffect(() => {
    let ignore = false
    getAlternatives(courseId, placeId)
      .then((res) => !ignore && setData(res))
      .catch((e) => !ignore && setLoadError(toFail(e)))
    return () => {
      ignore = true
    }
  }, [courseId, placeId])

  if (loadError && isExpiredError(loadError.code)) return <ExpiredState />
  if (loadError) return <EmptyState sticker="sceneEmpty" message="교체 후보를 불러오지 못했어요" description={loadError.message} />
  if (!data) return <LoadingState message="바꿀 만한 곳을 찾고 있어요" />

  const current = data.currentPlace

  const handleReplace = async () => {
    if (!selectedId || replacing) return
    setReplacing(true)
    try {
      // 교체 결과는 새 recommendationId 로 보관된다 (recommend-api.md 5장) → 새 ID 의 상세로 이동
      const replaced = await replacePlace(courseId, { targetPlaceId: current.placeId, newPlaceId: selectedId })
      navigate(`/results/${replaced.recommendationId}`, { replace: true })
    } catch (e) {
      const fail = toFail(e)
      if (isExpiredError(fail.code)) setLoadError(fail)
      else setToast(fail.message) // 409 "선택한 시간을 넘어요." 등
      setReplacing(false)
    }
  }

  return (
    <>
      <div className="flex flex-col gap-5 px-4 py-5">
        <div className="flex items-center gap-2">
          <StickerImage name="turtleHead" className="size-11" />
          <p className="font-display text-xl">이 장소만 바꿔 볼까요?</p>
        </div>

        <section className="paper-card relative flex items-center gap-3 p-3.5">
          <span className="absolute -top-2.5 left-3 rounded-full border-2 border-ink bg-sunny px-2 text-[11px] font-bold">지금</span>
          <StickerImage name={CATEGORY_STICKER[current.category]} className="size-11" />
          <span className="min-w-0">
            <p className="truncate font-bold">{current.placeName}</p>
            <p className="text-xs text-neutral-500">
              {getCategoryLabel(current.category)} › {current.subCategory} · 체류 {formatMinutes(current.stayMinutes)}
            </p>
          </span>
        </section>

        <section>
          <h3 className="mb-2.5 px-1 font-display text-lg">바꿀 수 있는 곳</h3>
          {data.alternatives.length === 0 ? (
            <EmptyState message="근처에 바꿀 만한 곳이 없어요" description="다른 장소를 바꿔 보거나 다시 추천받아 보세요" />
          ) : (
            <ul className="flex flex-col gap-2">
              {data.alternatives.map((alt) => {
                const blocked = alt.exceedsAvailableTime
                const selected = selectedId === alt.placeId
                return (
                  <li key={alt.placeId}>
                    <button
                      type="button"
                      aria-pressed={selected}
                      disabled={blocked}
                      onClick={() => setSelectedId(alt.placeId)}
                      className={`flex w-full items-center gap-3 rounded-2xl border-2 p-3 text-left disabled:opacity-50 ${
                        selected ? 'border-primary bg-primary-light' : 'border-line bg-cream'
                      }`}
                    >
                      <StickerImage name={CATEGORY_STICKER[alt.category]} className="size-10" />
                      <span className="min-w-0 flex-1">
                        <span className="block truncate font-bold">{alt.placeName}</span>
                        <span className="text-xs text-neutral-500">
                          {alt.subCategory} · {alt.transitInfo ?? '이동'} {formatMinutes(alt.travelMinutes)}
                          {alt.estimated && ' (예상)'}
                        </span>
                      </span>
                      {blocked ? (
                        <span className="shrink-0 rounded-full bg-red-100 px-2 py-0.5 text-xs font-bold text-red-600">시간 초과</span>
                      ) : (
                        <span
                          className={`shrink-0 rounded-full px-2 py-0.5 text-xs font-bold ${alt.diffMinutes > 0 ? 'bg-red-100 text-red-600' : 'bg-turtle/40 text-ink'}`}
                        >
                          총 시간 {alt.diffMinutes > 0 ? '+' : alt.diffMinutes < 0 ? '−' : '±'}
                          {formatMinutes(Math.abs(alt.diffMinutes))}
                        </span>
                      )}
                    </button>
                  </li>
                )
              })}
            </ul>
          )}
        </section>
      </div>

      <BottomActionBar>
        <Button variant="pink" disabled={selectedId === null || replacing} onClick={() => void handleReplace()}>
          {replacing ? '바꾸는 중…' : '이 장소로 교체'}
        </Button>
      </BottomActionBar>

      {toast && <Toast message={toast} onClose={closeToast} />}
    </>
  )
}
