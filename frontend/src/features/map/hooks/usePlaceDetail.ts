import { useEffect, useState } from 'react'
import { ApiError, DEFAULT_ERROR_MESSAGE } from '../../../shared/api/apiError'
import { getPlaceDetail } from '../api/placeDetailApi'
import type { PlaceDetailResponse } from '../types'

export function usePlaceDetail(id: string, name: string, lat: number, lng: number) {
  const key = JSON.stringify([id, name, lat, lng])
  const [result, setResult] = useState<{ key: string; detail: PlaceDetailResponse | null; error: string | null } | null>(null)
  useEffect(() => {
    if (!id) return
    let cancelled = false
    getPlaceDetail(id, name, lat, lng)
      .then((detail) => { if (!cancelled) setResult({ key, detail, error: null }) })
      .catch((error: unknown) => {
        if (!cancelled) setResult({ key, detail: null, error: error instanceof ApiError ? error.message : DEFAULT_ERROR_MESSAGE })
      })
    return () => { cancelled = true }
  }, [id, name, lat, lng, key])
  const current = result?.key === key ? result : null
  return { detail: current?.detail ?? null, loading: Boolean(id) && current === null, error: current?.error ?? null }
}
