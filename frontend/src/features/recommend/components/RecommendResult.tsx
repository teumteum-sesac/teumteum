import { useCallback, useState } from 'react'
import { useNavigate } from 'react-router'
import BottomActionBar from '../../../shared/components/BottomActionBar'
import Button from '../../../shared/components/Button'
import Chip from '../../../shared/components/Chip'
import ChipGroup from '../../../shared/components/ChipGroup'
import EmptyState from '../../../shared/components/EmptyState'
import Icon from '../../../shared/components/Icon'
import LoadingState from '../../../shared/components/LoadingState'
import Toast from '../../../shared/components/Toast'
import { isExpiredError, useRecommendCourses, useRecommendStore } from '../store'
import ConditionSummary from './ConditionSummary'
import CourseCard from './CourseCard'
import ExpiredState from './ExpiredState'

const ALL = '전체'

/**
 * 추천 결과 목록 (RES-01)
 * - 외출 조건 [추천받기] 가 POST /api/recommendations 를 부르고(store.request), 이 화면이 결과를 그린다
 * - 계산 중: 공통 로딩 / 결과 없음(courses: []): 결과 없음 안내 / 2시간 지남(410): 만료 안내
 * - 세분류 필터: 서버가 준 subCategoryFilters (REQ-050). '전체' 칩은 프론트가 붙인다
 * - 다시 추천: POST /api/recommendations/{setId}/regenerate. 너무 자주 누르면 429 → 토스트
 */
export default function RecommendResult() {
  const navigate = useNavigate()
  const { result, loading, error, regenerate } = useRecommendStore()
  const courses = useRecommendCourses()
  const [subCategory, setSubCategory] = useState(ALL)
  const [toast, setToast] = useState<string | null>(null)
  const closeToast = useCallback(() => setToast(null), [])

  const subCategories = [...new Set(result?.subCategoryFilters.flatMap((f) => f.subCategories) ?? [])]
  const filtered = courses.filter((c) => subCategory === ALL || c.places.some((p) => p.subCategory === subCategory))

  const handleRegenerate = async () => {
    const failed = await regenerate()
    if (failed && !isExpiredError(failed.code)) {
      setToast(failed.message) // 429 "잠시 후 다시 시도해 주세요" 등 서버 문구 그대로
      return
    }
    setSubCategory(ALL)
  }

  if (loading) return <LoadingState message="딱 맞는 코스를 찾고 있어요" />
  if (error && isExpiredError(error.code)) return <ExpiredState />
  if (error) {
    return (
      <EmptyState
        sticker="sceneEmpty"
        message="추천을 받지 못했어요"
        description={error.message}
        action={
          <Button className="mx-auto w-auto px-8" onClick={() => navigate('/condition')}>
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
        {subCategories.length > 0 && (
          <ChipGroup>
            {[ALL, ...subCategories].map((name) => (
              <Chip key={name} selected={subCategory === name} onClick={() => setSubCategory(name)}>
                {name}
              </Chip>
            ))}
          </ChipGroup>
        )}
      </div>

      {filtered.length > 0 ? (
        <ul className="flex flex-col gap-5 p-4 pt-5">
          {filtered.map((course, i) => (
            <li key={course.courseId}>
              <CourseCard course={course} index={i + 1} to={`/results/${course.courseId}`} />
            </li>
          ))}
          <li className="text-center text-xs text-neutral-400">이동·체류시간은 예상값이라 실제와 다를 수 있어요</li>
        </ul>
      ) : (
        // 결과 없음 (REQ-027): 오류가 아니라 courses: [] 로 온다
        <EmptyState
          sticker="sceneEmpty"
          message={courses.length === 0 ? '조건에 맞는 추천이 없어요' : `‘${subCategory}’ 코스가 없어요`}
          description={courses.length === 0 ? '시간을 늘리거나 카테고리를 줄이면 새로운 코스를 찾아 올게요!' : '다른 세분류를 골라 보세요'}
        />
      )}

      <BottomActionBar>
        <Button variant="secondary" className="flex items-center justify-center gap-1.5" onClick={() => navigate('/condition')}>
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
