import { useRef, type PointerEvent } from 'react'
import { Link } from 'react-router'
import Icon from '../../../shared/components/Icon'
import StickerImage from '../../../shared/components/StickerImage'
import { formatMinutes, formatShortDate } from '../../../shared/utils/format'
import type { CourseSummaryResponse } from '../types'

// 부모 화면에서 전달받을 값의 타입이다.
interface Props {
    course: CourseSummaryResponse
    onRemove: () => void
    disabled?: boolean
    /** 목록 순번 (0부터). 모서리 테이프 색을 번갈아 고른다 */
    index?: number
}

/** 카드마다 테이프 색을 번갈아 붙여 스크랩북 느낌을 낸다 (추천 결과 카드와 같음) */
const TAPES = ['decoTapePink', 'decoTapeYellow'] as const

// 서버에서 받은 코스 요약 한 건을 카드로 표시한다.
export default function SavedCourseCard({ course, onRemove, disabled = false, index = 0 }: Props) {
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
            // 디자인: 추천 결과 카드(CourseCard)와 같은 종이 카드 + 핑크 띠 + 모서리 마스킹테이프
            className="paper-card relative overflow-visible"
        >
            <StickerImage
                name={TAPES[index % TAPES.length]}
                className="pointer-events-none absolute -top-4 -right-2 z-10 w-12 rotate-12"
                ratio="aspect-[3/2]"
            />
            <Link
                to={`/mypage/courses/${course.courseId}`}
                className="block active:translate-y-0.5"
                draggable={false}
                onClick={(e) => {
                    // 스와이프는 삭제 확인만 요청하고 상세 링크 이동은 막는다.
                    if (swiped.current) e.preventDefault()
                }}
            >
                {/* 위: 연한 핑크 띠에 코스명 (장소 이름을 → 로 이은 것) */}
                <div className="flex items-center gap-3 rounded-t-[14px] bg-primary-light/70 px-4 py-3">
                    <span className="flex size-10 shrink-0 items-center justify-center rounded-full border-2 border-ink bg-cream">
                        <StickerImage name="navSaved" className="size-7" />
                    </span>
                    <p className="line-clamp-2 min-w-0 flex-1 font-display text-[15px] leading-snug">{course.courseName}</p>
                    <Icon name="chevronRight" className="size-5 shrink-0 text-neutral-500" />
                </div>
            </Link>

            {/* 아래: 총 시간 · 장소 수 · 저장일 알약 + 삭제 */}
            <div className="flex items-center gap-1.5 px-4 py-3">
                <span className="flex items-center gap-1 rounded-full border-2 border-ink bg-sunny px-2.5 py-0.5 text-xs font-bold">
                    <Icon name="clock" className="size-3.5" strokeWidth={2.6} />
                    {formatMinutes(course.totalMinutes)}
                </span>
                <span className="flex items-center gap-1 rounded-full border-2 border-line bg-cream px-2.5 py-0.5 text-xs font-bold text-neutral-700">
                    <Icon name="location" className="size-3.5" strokeWidth={2.6} />
                    {course.placeCount}곳
                </span>
                <span className="text-[11px] text-neutral-500">{formatShortDate(course.createdAt.slice(0, 10))} 저장</span>
                <button
                    type="button"
                    disabled={disabled}
                    onClick={onRemove}
                    aria-label={`${course.courseName} 삭제`}
                    className="ml-auto flex shrink-0 items-center gap-0.5 rounded-full border-2 border-line bg-white/70 px-2.5 py-0.5 text-xs font-bold text-neutral-500 active:border-primary active:text-primary-dark disabled:opacity-50"
                >
                    <Icon name="close" className="size-3.5" strokeWidth={2.6} />
                    삭제
                </button>
            </div>
        </li>
    )
}
