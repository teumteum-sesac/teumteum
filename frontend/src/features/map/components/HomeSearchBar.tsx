import { useState } from 'react'
import { Link } from 'react-router'
import { CATEGORY_STICKER } from '../../../shared/assets/stickerAssets'
import Icon from '../../../shared/components/Icon'
import StickerImage from '../../../shared/components/StickerImage'
import { getCategoryLabel } from '../../../shared/types/domain'
import { MOCK_NEARBY } from '../mock'

/**
 * 홈 검색창 (HOM-01 ①): 장소·주소 검색. 결과 목록을 아래 시트에 먼저 보여주고, 누르면 장소 상세(DET-01)
 * 디자인: 크림 종이 카드 + 잉크 테두리 (지도 위에 붙인 스티커 메모 느낌)
 * TODO(정종일): 카카오 로컬 키워드·주소 검색 API 연동 (현재 목업에서 이름으로 찾음)
 */
export default function HomeSearchBar() {
  const [keyword, setKeyword] = useState('')
  const [submitted, setSubmitted] = useState('')

  const results = submitted ? MOCK_NEARBY.filter((p) => p.name.includes(submitted) || p.address.includes(submitted)) : []

  return (
    <div className="flex flex-col gap-2">
      <form
        onSubmit={(e) => {
          e.preventDefault()
          setSubmitted(keyword.trim())
        }}
        className="flex h-12 items-center gap-2 rounded-2xl border-2 border-ink bg-cream px-3.5 shadow-[0_3px_0_var(--color-ink)]"
      >
        <Icon name="search" className="size-5 text-ink" />
        <input
          type="search"
          value={keyword}
          onChange={(e) => {
            setKeyword(e.target.value)
            if (!e.target.value) setSubmitted('')
          }}
          placeholder="장소, 주소 검색"
          aria-label="장소, 주소 검색"
          className="h-full min-w-0 flex-1 bg-transparent text-sm outline-none placeholder:text-neutral-400"
        />
      </form>

      {submitted && (
        <div className="paper-card max-h-72 overflow-y-auto">
          {results.length === 0 ? (
            <p className="p-4 text-sm text-neutral-500">‘{submitted}’ 검색 결과가 없어요.</p>
          ) : (
            <ul>
              {results.map((place) => (
                <li key={place.placeId} className="border-b border-dashed border-line last:border-0">
                  <Link to={`/results/place-${place.placeId}`} className="flex items-center gap-3 px-3 py-2.5">
                    <StickerImage name={CATEGORY_STICKER[place.category]} className="size-9" />
                    <div className="min-w-0">
                      <p className="truncate text-sm font-bold">{place.name}</p>
                      <p className="truncate text-xs text-neutral-500">
                        {getCategoryLabel(place.category)} · {place.address}
                      </p>
                    </div>
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  )
}
