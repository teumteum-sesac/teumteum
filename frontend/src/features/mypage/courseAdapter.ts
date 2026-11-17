import type { Course } from '../recommend/types'
import type { CourseResponse } from './types'

// 일정·지도 컴포넌트가 사용하는 이름으로 변환한다. 시간과 좌표는 재계산하지 않는다.
export function toSavedCourse(response: CourseResponse): Course {
  return {
    courseId: response.courseId,
    origin: {
      name: response.origin.name ?? '출발 위치',
      address: response.origin.address ?? undefined,
      lat: response.origin.latitude,
      lng: response.origin.longitude,
    },
    destination: response.destination ? {
      name: response.destination.name ?? '도착 위치',
      address: response.destination.address ?? undefined,
      lat: response.destination.latitude,
      lng: response.destination.longitude,
    } : null,
    transportType: response.transportType,
    startTime: response.startTime,
    finalTravelMinutes: response.finalTravelMinutes,
    totalMinutes: response.totalMinutes,
    estimated: response.estimated,
    places: response.places.map((place) => ({
      placeId: place.placeId,
      name: place.placeName,
      category: place.category,
      subCategory: place.subCategory,
      address: place.address ?? '주소 정보 없음',
      lat: place.latitude,
      lng: place.longitude,
      imageUrl: place.imageUrl ?? undefined,
      description: place.description ?? undefined,
      openingHours: place.openingHours ?? undefined,
      transitInfo: place.transitInfo ?? undefined,
      visitOrder: place.visitOrder,
      stayMinutes: place.stayMinutes,
      travelMinutes: place.travelMinutes,
    })),
  }
}
