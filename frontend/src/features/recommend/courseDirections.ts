import type { LocationValue } from '../../shared/types/domain'
import type { Course } from './types'

/** 차량·도보의 방문 순서를 카카오맵 길찾기에 전달한다. 도착지가 없으면 마지막 장소에서 끝난다. */
export function getCourseDirectionsUrl(course: Course): string | null {
  if (course.transportType === 'TRANSIT' || course.places.length === 0) return null
  const places = [...course.places].sort((a, b) => a.visitOrder - b.visitOrder)
  const points: LocationValue[] = [course.origin, ...places]
  if (course.destination) points.push(course.destination)
  // 카카오 링크의 경로 구분자로 해석되는 문자는 표시용 전각 문자로 바꾼다.
  const path = points.map((point) => {
    const name = point.name.replaceAll('/', '／').replaceAll(',', '，')
    return `${encodeURIComponent(name)},${point.lat},${point.lng}`
  }).join('/')
  const mode = course.transportType === 'CAR' ? 'car' : 'walk'
  return `https://map.kakao.com/link/by/${mode}/${path}`
}
