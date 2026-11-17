import { useRef, type PointerEvent } from 'react'
import { Link } from 'react-router'
import { formatMinutes, formatShortDate } from '../../../shared/utils/format'
import type { CourseSummaryResponse } from '../types'

// 부모 화면에서 전달받을 값의 타입이다.
interface Props {
    course: CourseSummaryResponse
    onRemove: () => void
    disabled?: boolean
}

// 서버에서 받은 코스 요약 한 건을 카드로 표시한다.
export default function SavedCourseCard({ course, onRemove, disabled = false }: Props) {
    // 손가락이나 마우스로 누르기 시작한 가로 위치를 기억한다.
    const startX = useRef<number | null>(null)
    // 스와이프 직후의 클릭으로 상세 화면까지 이동하지 않도록 구분한다.
    const swiped = useRef(false)

    const onPointerDown = (e: PointerEvent<HTMLLIElement>) => {
        startX.current = e.clientX
        swiped.current = false
    }

    const onPointerUp = (e: PointerEvent<HTMLLIElement>) => {
        // 왼쪽으로 60px 이상 이동했으면 삭제 확인을 요청한다.
        if (!disabled && startX.current !== null && startX.current - e.clientX > 60) {
            swiped.current = true
            onRemove()
        }

        startX.current = null
    }

    return (
        <li
            onPointerDown={onPointerDown}
            onPointerUp={onPointerUp}
            onPointerCancel={() => {
                // 스크롤 등으로 제스처가 취소되면 이전 위치를 버린다.
                startX.current = null
                swiped.current = false
            }}
            className="flex items-center gap-2 rounded-2xl border border-line p-4"
        >
            <Link
                to={`/mypage/courses/${course.courseId}`}
                className="min-w-0 flex-1"
                draggable={false}
                onClick={(e) => {
                    // 스와이프는 삭제 확인만 요청하고 상세 링크 이동은 막는다.
                    if (swiped.current) e.preventDefault()
                }}
            >
                <p className="truncate font-bold">{course.courseName}</p>

                <p className="mt-1 text-xs text-neutral-500">
                    {course.placeCount}곳 · {formatMinutes(course.totalMinutes)}
                    {' · '}
                    {formatShortDate(course.createdAt.slice(0, 10))} 저장
                </p>
            </Link>

            <button
                type="button"
                disabled={disabled}
                onClick={onRemove}
                className="shrink-0 rounded-lg border border-line px-2 py-1 text-xs text-neutral-500"
            >
                저장 해제
            </button>
        </li>
    )
}
