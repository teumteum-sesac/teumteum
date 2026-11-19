/*
 * [이 파일이 하는 일]
 * 백엔드 공통 응답(ApiResponse)의 모양을 TypeScript 타입으로 적어 둔다.
 * 백엔드 global/common 의 ApiResponse·ErrorDetail·FieldErrorDetail(정종일)과 이름·필드를 똑같이 맞췄다.
 *
 *   성공: { "success": true,  "data": { ... }, "error": null }
 *   실패: { "success": false, "data": null,    "error": { "code": "...", "message": "...", "fieldErrors": [...] } }
 *
 * 화면 코드에서 이 타입을 직접 다룰 일은 거의 없다.
 * shared/api/request.ts 의 apiGet·apiPost 가 data 만 꺼내 주고, 실패는 ApiError 로 바꿔 던지기 때문이다.
 */

/** 입력값 검증 실패 항목 1개 (예: { field: 'email', message: '이메일 형식을 확인해 주세요.' }) */
export interface FieldErrorDetail {
  field: string
  message: string
}

/** 실패 응답의 error 부분 */
export interface ErrorDetail {
  /** 프론트가 분기에 쓰는 코드 (예: 'INVALID_REQUEST', 'DUPLICATE_EMAIL') */
  code: string
  /** 화면에 그대로 보여 줄 문구 */
  message: string
  /** 입력값 검증 실패일 때만 있음 */
  fieldErrors?: FieldErrorDetail[]
}

/** 모든 API 응답의 공통 모양. T = data 의 타입 (예: ApiResponse<SignupResponse>) */
export interface ApiResponse<T> {
  success: boolean
  data: T | null
  error: ErrorDetail | null
}
