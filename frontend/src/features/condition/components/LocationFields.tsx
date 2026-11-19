import { Link } from 'react-router'
import Icon, { type IconName } from '../../../shared/components/Icon'
import { formatLocationLabel } from '../../../shared/types/domain'
import { useConditionStore } from '../store'

/**
 * 출발지·도착지 입력란 (둘 다 필수, 기본값 현재 위치 — ConditionForm 이 채움). 누르면 위치 설정(CON-02)으로 이동.
 * 디자인 (목업 CON-01): 핀·깃발 아이콘 + 두 줄(이름 / 값) 종이 카드
 */
export default function LocationFields() {
  const origin = useConditionStore((s) => s.origin)
  const destination = useConditionStore((s) => s.destination)

  return (
    <div className="flex flex-col gap-2">
      <LocationRow
        to="/condition/location?type=origin"
        icon="location"
        label="출발지"
        value={origin ? formatLocationLabel(origin) : undefined}
        placeholder="출발지를 설정해 주세요"
      />
      <LocationRow
        to="/condition/location?type=destination"
        icon="flag"
        label="도착지"
        value={destination ? formatLocationLabel(destination) : undefined}
        placeholder="도착지를 설정해 주세요"
      />
      {(origin === null || destination === null) && (
        <p className="px-1 text-xs text-primary-dark">현재 위치를 사용할 수 없어요. 출발지와 도착지를 직접 설정해 주세요.</p>
      )}
    </div>
  )
}

interface LocationRowProps {
  to: string
  icon: IconName
  label: string
  value?: string
  placeholder: string
}

function LocationRow({ to, icon, label, value, placeholder }: LocationRowProps) {
  return (
    <Link to={to} className="paper-card flex items-center gap-3 px-3.5 py-2.5 active:translate-y-0.5">
      <span className="flex size-9 shrink-0 items-center justify-center rounded-full bg-primary-light text-primary-dark">
        <Icon name={icon} className="size-5" />
      </span>
      <span className="flex min-w-0 flex-1 flex-col">
        <span className="text-[13px] font-bold">{label}</span>
        <span className={`truncate text-xs ${value ? 'text-neutral-700' : 'text-neutral-400'}`}>{value ?? placeholder}</span>
      </span>
      <Icon name="chevronRight" className="size-4 text-neutral-400" />
    </Link>
  )
}
