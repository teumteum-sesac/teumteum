import { useCallback, useState } from 'react'
import { useNavigate } from 'react-router'
import BottomActionBar from '../../../shared/components/BottomActionBar'
import Button from '../../../shared/components/Button'
import Chip from '../../../shared/components/Chip'
import ChipGroup from '../../../shared/components/ChipGroup'
import EmptyState from '../../../shared/components/EmptyState'
import Icon from '../../../shared/components/Icon'
import Toast from '../../../shared/components/Toast'
import { CATEGORIES } from '../../../shared/types/domain'
import { isRandomCondition, useConditionStore } from '../../condition/store'
import { useRecommendStore } from '../store'
import ConditionSummary from './ConditionSummary'
import CourseCard from './CourseCard'

const ALL = '전체'

/** 한 번에 보여주는 코스 수 (화면설계서 결정 3: 3개, 정렬 없음) */
const COURSE_COUNT = 3

/**
 * 추천 결과 목록 (RES-01)
 * TODO(나혜빈): 추천 API 연동 (현재 mock). 빈 결과면 백엔드가 반경을 넓혀 다시 찾는다
 */
export default function RecommendResult() {
  const navigate = useNavigate()
  const condition = useConditionStore()
  const { courses, reload } = useRecommendStore()
  const [subCategory, setSubCategory] = useState(ALL)
  const [toast, setToast] = useState<string | null>(null)
  const closeToast = useCallback(() => setToast(null), [])

  // 세분류 필터: 선택한 대분류의 세분류. 랜덤이면 결과 장소들의 세분류
  const subCategories = isRandomCondition(condition)
    ? [...new Set(courses.flatMap((c) => c.places.map((p) => p.subCategory)))]
    : CATEGORIES.filter((c) => condition.categories.includes(c.code)).flatMap((c) => c.subCategories)

  const filtered = courses
    .slice(0, COURSE_COUNT)
    .filter((c) => subCategory === ALL || c.places.some((p) => p.subCategory === subCategory))

  const handleReload = () => {
    if (!reload()) {
      setToast('잠시 후 다시 시도해 주세요')
      return
    }
    setSubCategory(ALL)
  }

  return (
    <>
      <div className="flex flex-col gap-3 px-4 pt-4">
        <ConditionSummary />
        <ChipGroup>
          {[ALL, ...subCategories].map((name) => (
            <Chip key={name} selected={subCategory === name} onClick={() => setSubCategory(name)}>
              {name}
            </Chip>
          ))}
        </ChipGroup>
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
        // 목업 '추천 결과 없음': 듀오가 지도 보며 고민하는 장면 + 친근한 문구. 버튼은 아래 고정 바에 있다
        <EmptyState
          sticker="sceneEmpty"
          message="조건에 맞는 추천이 없어요"
          description="시간을 늘리거나 카테고리를 줄이면 새로운 코스를 찾아 올게요!"
        />
      )}

      <BottomActionBar>
        <Button variant="secondary" className="flex items-center justify-center gap-1.5" onClick={() => navigate('/condition')}>
          <Icon name="sliders" className="size-5" />
          조건 바꾸기
        </Button>
        <Button className="flex items-center justify-center gap-1.5" onClick={handleReload}>
          <Icon name="refresh" className="size-5" strokeWidth={2.6} />
          다시 추천
        </Button>
      </BottomActionBar>

      {toast && <Toast message={toast} onClose={closeToast} />}
    </>
  )
}
