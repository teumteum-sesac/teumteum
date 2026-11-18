import type { CourseLatestPlacesResponse } from '../types'

// 저장 일정·지도에 합치지 않고 별도 영역에만 표시한다.
export default function LatestPlacePanel({ data }: { data: CourseLatestPlacesResponse }) {
  return (
    <section className="mx-4 mb-6 rounded-2xl border-2 border-dashed border-primary p-4" aria-label="최신 조회 정보">
      <h3 className="font-display text-lg">최신 조회 정보</h3>
      <p className="mt-1 text-xs text-neutral-500">조회 요청: {new Date(data.requestedAt).toLocaleString('ko-KR')}</p>
      <p className="mt-2 text-xs text-neutral-500">저장한 일정과 코스 지도는 저장 시점 정보를 유지해요.</p>
      <ul className="mt-4 flex flex-col gap-4">
        {data.places.map((item) => (
          <li key={item.coursePlaceId} className="rounded-xl bg-paper p-3">
            <p className="text-sm font-bold">{item.visitOrder}번째 장소</p>
            {item.status === 'SUCCESS' && item.place ? (
              <div className="mt-2 flex flex-col gap-2 text-sm">
                <p className="font-bold">{item.place.placeName}</p>
                {item.place.subCategory && <p>{item.place.subCategory}</p>}
                <p>{item.place.address ?? '주소 정보 없음'}</p>
                {item.place.imageUrl && (
                  <figure>
                    <img src={item.place.imageUrl} alt={item.place.placeName} className="max-h-48 w-full rounded-xl object-cover" onError={(e) => { e.currentTarget.hidden = true }} />
                    <figcaption className="mt-1 text-xs text-neutral-500">{item.place.imageSource}{item.place.imageCopyrightType ? ` · ${item.place.imageCopyrightType}` : ''}</figcaption>
                  </figure>
                )}
                <p className="whitespace-pre-line">{item.place.description ?? '소개 정보 없음'}</p>
                <p className="whitespace-pre-line">운영시간: {item.place.openingHours ?? '정보 없음'}</p>
                {item.fetchedAt && <p className="text-xs text-neutral-500">조회: {new Date(item.fetchedAt).toLocaleString('ko-KR')}</p>}
              </div>
            ) : (
              <p className="mt-2 text-sm text-neutral-500">{item.message ?? (item.status === 'NOT_FOUND' ? '장소 정보를 확인할 수 없어요.' : '최신 정보를 불러오지 못했어요.')}</p>
            )}
          </li>
        ))}
      </ul>
    </section>
  )
}
