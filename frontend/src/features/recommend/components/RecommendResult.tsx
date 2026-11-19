import { useCallback, useState } from 'react'
import { useNavigate } from 'react-router'
import BottomActionBar from '../../../shared/components/BottomActionBar'
import Button from '../../../shared/components/Button'
import Chip from '../../../shared/components/Chip'
import ChipGroup from '../../../shared/components/ChipGroup'
import EmptyState from '../../../shared/components/EmptyState'
import Icon from '../../../shared/components/Icon'
import LoadingState from '../../../shared/components/LoadingState'
import { useRaceLoading } from '../../../shared/hooks/useRaceLoading'
import type { CategoryCode } from '../../../shared/types/domain'
import Toast from '../../../shared/components/Toast'
import { isExpiredError, useRecommendCourses, useRecommendStore } from '../store'
import ConditionSummary from './ConditionSummary'
import CourseCard from './CourseCard'
import ExpiredState from './ExpiredState'

const ALL = '전체'
/** 조건 화면에 "결과에서 왔다" 고 알려 준다 → [추천받기] 가 결과 화면을 새로 쌓지 않고 뒤로 돌아온다 (ConditionForm) */
const FROM_RESULTS = { state: { fromResults: true } }

/**
 * 추천 결과 목록 (RES-01)
 * - 외출 조건 [추천받기] 가 POST /api/recommendations 를 부르고(store.request), 이 화면이 결과를 그린다
 * - 계산 중: 공통 로딩 / 결과 없음(courses: []): 결과 없음 안내 / 2시간 지남(410): 만료 안내
 * - 세분류 필터 (REQ-050, 화면설계서 RES-01 ②): 두 단계 칩. 서버가 준 subCategoryFilters(대분류별 묶음)로 그린다
 *   - 첫 줄: [전체] + 대분류. 대분류만 고르면 그 대분류 장소가 들어간 코스
 *   - 둘째 줄(대분류를 골랐을 때만): 그 대분류의 세분류. 고르면 그 세분류 장소가 들어간 코스, 다시 누르면 해제
 * - 다시 추천: POST /api/recommendations/{setId}/regenerate. 너무 자주 누르면 429 → 토스트
 */
export default function RecommendResult() {
  const navigate = useNavigate()
  const { result, loading, error, regenerate } = useRecommendStore()
  // 로딩 화면은 토끼·거북이가 깃발에 도착할 때까지 유지 (추천·다시 추천 모두)
  const race = useRaceLoading(loading)
  const courses = useRecommendCourses()
  const [category, setCategory] = useState<CategoryCode | null>(null) // null = 전체
  const [subCategory, setSubCategory] = useState<string | null>(null) // null = 고른 대분류 전체
  const [toast, setToast] = useState<string | null>(null)
  const closeToast = useCallback(() => setToast(null), [])

  const filters = result?.subCategoryFilters ?? []
  const selectedFilter = filters.find((f) => f.category === category)
  const filtered = courses.filter(
    (c) =>
      category === null ||
      c.places.some((p) => p.category === category && (subCategory === null || p.subCategory === subCategory)),
  )

  const selectCategory = (next: CategoryCode | null) => {
    setCategory(next)
    setSubCategory(null) // 대분류를 바꾸면 세분류 선택은 풀린다
  }
  const toggleSubCategory = (name: string) => setSubCategory((current) => (current === name ? null : name))

  const handleRegenerate = async () => {
    const failed = await regenerate()
    if (failed && !isExpiredError(failed.code)) {
      setToast(failed.message) // 429 "잠시 후 다시 시도해 주세요" 등 서버 문구 그대로
      return
    }
    selectCategory(null) // 새 결과는 '전체' 부터
  }

  // 토끼·거북이가 깃발에 도착한 뒤에 결과를 보여 준다 (최소 2초, 늦으면 그만큼 더)
  if (race.show) return <LoadingState message="딱 맞는 코스를 찾고 있어요" readyAt={race.readyAt} />
  if (error && isExpiredError(error.code)) return <ExpiredState />
  if (error) {
    return (
      <EmptyState
        sticker="sceneEmpty"
        message="추천을 받지 못했어요"
        description={error.message}
        action={
          <Button className="mx-auto w-auto px-8" onClick={() => navigate('/condition', FROM_RESULTS)}>
            조건 다시 고르기
          </Button>
        }
      />
    )
  }
  if (!result) {
    return (
      <EmptyState
        sticker="sceneHome"
        message="아직 추천받은 코스가 없어요"
        description="외출 조건을 고르고 [추천받기] 를 눌러 주세요"
        action={
          <Button className="mx-auto w-auto px-8" onClick={() => navigate('/condition')}>
            외출 조건 고르기
          </Button>
        }
      />
    )
  }

  return (
    <>
      <div className="flex flex-col gap-3 px-4 pt-4">
        <ConditionSummary />
        {courses.length > 0 && filters.length > 0 && (
          <ChipGroup arrows>
            <Chip selected={category === null} onClick={() => selectCategory(null)}>
              {ALL}
            </Chip>
            {filters.map((f) => (
              <Chip key={f.category} selected={category === f.category} onClick={() => selectCategory(f.category)}>
                {f.categoryName}
              </Chip>
            ))}
          </ChipGroup>
        )}
        {/* 세분류: 고른 대분류 아래에 딸린 단계로 보이게, 연한 핑크 받침 위에 작은 칩(level="sub")으로 */}
        {selectedFilter && (
          <div className="-mt-1 rounded-2xl bg-primary-light/40 px-3 py-2">
            <ChipGroup>
              {selectedFilter.subCategories.map((name) => (
                <Chip key={name} level="sub" selected={subCategory === name} onClick={() => toggleSubCategory(name)}>
                  {name}
                </Chip>
              ))}
            </ChipGroup>
          </div>
        )}
      </div>

      {filtered.length > 0 ? (
        <ul className="flex flex-col gap-5 p-4 pt-5">
          {filtered.map((course, i) => (
            <li key={course.courseId}>
              {/* 코스를 누르면 지도부터 보여 준다 (RES-01 → DET-02 → DET-01, 팀 회의 결정 #177) */}
              <CourseCard course={course} index={i + 1} to={`/results/${course.courseId}/map`} />
            </li>
          ))}
          <li className="text-center text-xs text-neutral-400">이동·체류시간은 예상값이라 실제와 다를 수 있어요</li>
        </ul>
      ) : (
        // 결과 없음 (REQ-027): 오류가 아니라 courses: [] 로 온다
        <EmptyState
          sticker="sceneEmpty"
          message={
            courses.length === 0
              ? '조건에 맞는 추천이 없어요'
              : `‘${subCategory ?? selectedFilter?.categoryName ?? ALL}’ 코스가 없어요`
          }
          description={courses.length === 0 ? '시간을 늘리거나 카테고리를 줄이면 새로운 코스를 찾아 올게요!' : '다른 카테고리를 골라 보세요'}
        />
      )}

      <BottomActionBar>
        <Button variant="secondary" className="flex items-center justify-center gap-1.5" onClick={() => navigate('/condition', FROM_RESULTS)}>
          <Icon name="sliders" className="size-5" />
          조건 바꾸기
        </Button>
        <Button className="flex items-center justify-center gap-1.5" onClick={() => void handleRegenerate()}>
          <Icon name="refresh" className="size-5" strokeWidth={2.6} />
          다시 추천
        </Button>
      </BottomActionBar>

      {toast && <Toast message={toast} onClose={closeToast} />}
    </>
  )
}
