import { apiDelete, apiGet, apiPatch } from '../../../shared/api/request'
import type { MemberResponse, MemberUpdateRequest, MemberWithdrawRequest, MemberWithdrawResponse } from '../types'

export function getMyInfo() { return apiGet<MemberResponse>('/members/me') }
export function updateMyInfo(body: MemberUpdateRequest) { return apiPatch<MemberResponse>('/members/me', body) }
export function withdrawMyAccount(body: MemberWithdrawRequest) { return apiDelete<MemberWithdrawResponse>('/members/me', body) }
