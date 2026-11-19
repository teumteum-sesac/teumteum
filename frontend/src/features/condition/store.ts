import { create } from 'zustand'
import type { CategoryCode, CategoryMode, LocationValue, TransportType } from '../../shared/types/domain'
import { nowRoundedTime } from '../../shared/utils/format'

/**
 * 외출 조건 (CON-01).
 * 결과 화면에서 '조건 바꾸기'로 돌아와도 입력값이 유지되도록 전역 상태로 둔다.
 * 새로고침·앱 재실행 시에는 초기화된다.
 */
interface ConditionState {
  origin: LocationValue | null
  destination: LocationValue | null
  /** 시작 시각 "HH:mm" */
  startTime: string
  /** 이용 가능 시간(분). 종료 시각 = 시작 시각 + 이 값 */
  availableMinutes: number
  transportType: TransportType
  /** 고른 대분류가 있으면 DIRECT, 없으면 RANDOM (categories 와 항상 함께 바뀐다) */
  categoryMode: CategoryMode
  /** 직접 선택한 대분류. 비어 있으면 랜덤으로 진행 */
  categories: CategoryCode[]

  setOrigin: (origin: LocationValue | null) => void
  setDestination: (destination: LocationValue | null) => void
  setStartTime: (startTime: string) => void
  setAvailableMinutes: (minutes: number) => void
  setTransportType: (transportType: TransportType) => void
  setCategoryMode: (mode: CategoryMode) => void
  toggleCategory: (category: CategoryCode) => void
  reset: () => void
}

/** 여유 시간 기본값(분): 1시간. 조건 화면에 새로 들어올 때마다 이 값으로 시작한다 (ConditionForm) */
export const DEFAULT_AVAILABLE_MINUTES = 60

const initialState = () => ({
  origin: null,
  destination: null,
  startTime: nowRoundedTime(),
  availableMinutes: DEFAULT_AVAILABLE_MINUTES,
  transportType: 'WALK' as TransportType, // 기본 도보
  categoryMode: 'RANDOM' as CategoryMode,
  categories: [] as CategoryCode[],
})

export const useConditionStore = create<ConditionState>((set) => ({
  ...initialState(),
  setOrigin: (origin) => set({ origin }),
  setDestination: (destination) => set({ destination }),
  setStartTime: (startTime) => set({ startTime }),
  setAvailableMinutes: (availableMinutes) => set({ availableMinutes }),
  setTransportType: (transportType) => set({ transportType }),
  // 랜덤 추천을 누르면 고른 카테고리를 모두 푼다. 직접 선택은 아무것도 안 골랐으면 문화생활 1개로 시작한다 (CategorySelector)
  setCategoryMode: (categoryMode) => set(categoryMode === 'RANDOM' ? { categoryMode, categories: [] } : { categoryMode }),
  // 카테고리를 고르면 직접 선택, 전부 풀면 랜덤 추천으로 바뀐다
  toggleCategory: (category) =>
    set((state) => {
      const categories = state.categories.includes(category)
        ? state.categories.filter((c) => c !== category)
        : [...state.categories, category]
      return { categories, categoryMode: categories.length > 0 ? 'DIRECT' : 'RANDOM' }
    }),
  reset: () => set(initialState()),
}))

/** 실제로 랜덤 추천인지 (랜덤 선택 또는 직접 선택에서 아무것도 안 고름) */
export function isRandomCondition(state: Pick<ConditionState, 'categoryMode' | 'categories'>): boolean {
  return state.categoryMode === 'RANDOM' || state.categories.length === 0
}
