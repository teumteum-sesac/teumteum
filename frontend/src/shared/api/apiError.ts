import type { FieldErrorDetail } from '../types/api'

/*
 * [이 파일이 하는 일]
 * API 호출이 실패했을 때 화면 코드가 받는 오류 객체.
 * 백엔드 오류(400·401·409·500…), 서버 꺼짐, 시간 초과를 전부 이 모양 하나로 통일한다.
 *
 * [화면에서 쓰는 법]
 *   try {
 *     await signup(form)
 *   } catch (e) {
 *     if (e instanceof ApiError) {
 *       if (e.code === 'DUPLICATE_EMAIL') ...          // 오류 종류별 분기
 *       setError(e.message)                             // 화면 안내 문구 (백엔드가 준 문장 그대로)
 *       e.getFieldMessage('email')                      // 특정 입력칸 오류 문구
 *     }
 *   }
 */
export class ApiError extends Error {
  /** 백엔드 error.code. 네트워크 문제면 'NETWORK_ERROR' */
  readonly code: string
  /** HTTP 상태 코드. 서버에 닿지도 못했으면 0 */
  readonly status: number
  /** 입력값 검증 실패 항목 (없으면 빈 배열) */
  readonly fieldErrors: FieldErrorDetail[]

  constructor(code: string, message: string, status: number, fieldErrors: FieldErrorDetail[] = []) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.status = status
    this.fieldErrors = fieldErrors
  }

  /** 특정 입력칸(field)의 오류 문구. 없으면 undefined → TextField 의 error 로 그대로 넘기면 된다 */
  getFieldMessage(field: string): string | undefined {
    return this.fieldErrors.find((f) => f.field === field)?.message
  }
}

/** 서버에 연결하지 못했을 때 (서버 꺼짐, 인터넷 끊김, 시간 초과) */
export const NETWORK_ERROR = 'NETWORK_ERROR'

/** 백엔드가 공통 형식이 아닌 응답을 줬을 때 쓰는 기본 문구 */
export const DEFAULT_ERROR_MESSAGE = '일시적인 오류가 발생했어요. 잠시 후 다시 시도해 주세요.'
