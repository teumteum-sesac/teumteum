import { useEffect, useRef } from 'react'
import { PAPER_STYLE } from '../../../shared/assets/stickerAssets'
import ArrowScrollRow from '../../../shared/components/ArrowScrollRow'
import Icon from '../../../shared/components/Icon'
import { formatMinutes } from '../../../shared/utils/format'
import { buildSchedule, getTotalTravelMinutes, type Course } from '../../recommend/types'

/*
 * [이 파일이 하는 일]
 * 코스 지도(DET-02) 아래 코스 패널. 높이가 고정돼 있다 (끌어 올리기·펼치기 없음).
 *   1줄: 총 시간 · 이동 시간
 *   2줄: 지점 칩 (S → 1 → 2 → E). 하나를 골라도 그대로 남아 있어서 바로 다음 지점을 누를 수 있다
 *   3줄: 고른 지점 정보. 장소면 방문 시각·체류시간 + [상세보기 >] (장소 상세 화면 /places/:placeId)
 * 지점 id 는 CourseMap 마커와 같다: 출발 'S', 장소 visitOrder('1'…), 도착 'E'
 */

interface CourseMapSheetProps {
  course: Course
  /** 고른 지점 id ('S', '1'…, 'E'). 없으면 null */
  selectedId: string | null
  /** 칩을 눌렀을 때 */
  onSelect: (id: string) => void
  /** 고른 장소의 [상세보기] (장소 상세 화면으로 이동) */
  onOpenPlace: () => void
}

interface Stop {
  id: string
  label: string
  name: string
  /** 출발·도착(노랑 원) */
  dark: boolean
}

export default function CourseMapSheet({ course, selectedId, onSelect, onOpenPlace }: CourseMapSheetProps) {
  const listRef = useRef<HTMLOListElement>(null)
  const { rows, endTime } = buildSchedule(course)

  const stops: Stop[] = [
    { id: 'S', label: 'S', name: course.origin.name, dark: true },
    ...course.places.map((p) => ({ id: String(p.visitOrder), label: String(p.visitOrder), name: p.name, dark: false })),
    ...(course.destination ? [{ id: 'E', label: 'E', name: course.destination.name, dark: true }] : []),
  ]
  const selectedStop = stops.find((s) => s.id === selectedId) ?? null
  const selectedRow = rows.find((r) => String(r.place.visitOrder) === selectedId) ?? null

  // 지도 마커로 고른 칩이 줄 밖에 있으면 보이게 넘긴다
  useEffect(() => {
    if (!selectedId) return
    listRef.current
      ?.querySelector(`[data-stop-id="${CSS.escape(selectedId)}"]`)
      ?.scrollIntoView({ block: 'nearest', inline: 'nearest', behavior: 'smooth' })
  }, [selectedId])

  // 고른 지점 설명: 장소면 방문 시각·체류, 출발이면 출발 시각, 도착이면 도착 시각
  let info: string | null = null
  if (selectedRow) info = `${selectedRow.arrive} ~ ${selectedRow.leave} · 체류 ${formatMinutes(selectedRow.place.stayMinutes)}`
  else if (selectedId === 'S') info = `출발 ${course.startTime}`
  else if (selectedId === 'E') info = `도착 ${endTime}`

  return (
    <div
      style={PAPER_STYLE}
      className="absolute inset-x-0 bottom-0 z-20 flex h-44 flex-col gap-2 overflow-hidden rounded-t-[28px] border-2 border-b-0 border-line bg-cream px-4 pt-3.5 pb-3 shadow-[0_-4px_16px_rgba(0,0,0,0.08)]"
    >
      <div className="flex items-center gap-2 px-1">
        <span className="flex items-center gap-1 rounded-full border-2 border-ink bg-sunny px-2.5 py-0.5 text-sm font-bold">
          <Icon name="clock" className="size-4" strokeWidth={2.6} />총 {formatMinutes(course.totalMinutes)}
        </span>
        <span className="text-xs text-neutral-500">이동 {formatMinutes(getTotalTravelMinutes(course))}</span>
      </div>

      {/* 지점 칩: 골라도 그대로 두어 다음 지점을 바로 누를 수 있다. 고른 칩은 핑크. 잘리면 양 끝 < > */}
      <ArrowScrollRow className="py-0.5" itemName="지점">
        <ol ref={listRef} className="flex w-max items-center gap-1">
          {stops.map((s, i) => {
            const selected = s.id === selectedId
            return (
              <li key={s.id} className="flex shrink-0 items-center gap-1">
                {i > 0 && <Icon name="chevronRight" className="size-3.5 text-neutral-400" />}
                <button
                  type="button"
                  data-stop-id={s.id}
                  aria-pressed={selected}
                  onClick={() => onSelect(s.id)}
                  className={`flex max-w-36 items-center gap-1.5 rounded-full border-2 py-1 pr-2.5 pl-1 text-xs ${
                    selected ? 'border-primary bg-primary-light font-bold' : 'border-line bg-white/70'
                  }`}
                >
                  <Badge label={s.label} dark={s.dark} />
                  <span className="truncate">{s.name}</span>
                </button>
              </li>
            )
          })}
        </ol>
      </ArrowScrollRow>

      {/* 고른 지점 정보 + 상세보기. 아무것도 안 골랐으면 안내 문구 */}
      {selectedStop ? (
        <div className="flex min-h-0 items-center gap-2 rounded-2xl bg-white/70 px-3 py-2">
          <div className="min-w-0 flex-1">
            <p className="truncate text-sm font-bold">{selectedStop.name}</p>
            {info && <p className="text-[11px] text-neutral-500">{info}</p>}
          </div>
          {selectedRow && (
            <button
              type="button"
              onClick={onOpenPlace}
              className="flex shrink-0 items-center gap-0.5 rounded-full border-2 border-ink bg-primary px-3 py-1.5 text-xs font-bold text-white"
            >
              상세보기
              <Icon name="chevronRight" className="size-3.5" strokeWidth={2.8} />
            </button>
          )}
        </div>
      ) : (
        <p className="px-1 text-[11px] text-neutral-400">지도 번호나 장소를 누르면 그 위치로 이동해요</p>
      )}
    </div>
  )
}

/** 번호 원 (장소: 핑크, 출발·도착: 노랑) */
function Badge({ label, dark }: { label: string; dark: boolean }) {
  return (
    <span
      className={`flex size-5 shrink-0 items-center justify-center rounded-full border-2 border-ink font-display text-[11px] ${
        dark ? 'bg-sunny text-ink' : 'bg-primary text-white'
      }`}
    >
      {label}
    </span>
  )
}
