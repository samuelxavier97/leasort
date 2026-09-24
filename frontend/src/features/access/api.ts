import { api } from '@/lib/api'
import type { Relationship } from '@/features/visits/api'

export type AccessResult = 'AUTHORIZED' | 'DENIED'
export type DenialReason = 'INVALID_CODE' | 'CANCELLED' | 'ALREADY_USED' | 'EXPIRED' | 'WRONG_DATE'

export interface AccessCompanion {
  id: string
  name: string
  relationship: Relationship
}

/** Resposta da validação e do registro (D-088): 200 nos dois casos, com `result`. */
export interface AccessResponse {
  result: AccessResult
  denialReason?: DenialReason
  invitationId?: string
  leadName?: string
  /** Na negativa, só em WRONG_DATE e EXPIRED. */
  scheduledDate?: string
  prospectorName?: string
  companions?: AccessCompanion[]
  accessRecordId?: string
  entryAt?: string
}

/** Linha de acessos recentes (D-092): sem código tentado e sem CPF. */
export interface RecentAccess {
  id: string
  createdAt: string
  result: AccessResult
  denialReason: DenialReason | null
  leadName: string | null
  gate: string
  validatedBy: string
}

export const recentAccessQueryKey = ['access', 'recent'] as const

/** Envia o texto do QR como foi lido ou o código digitado sem hífen; o backend normaliza (§12.1). */
export function validateAccess(code: string): Promise<AccessResponse> {
  return api<AccessResponse>('/api/access/validate', { method: 'POST', body: { code } })
}

export function registerAccess(invitationId: string, presentCompanionIds: string[]): Promise<AccessResponse> {
  return api<AccessResponse>('/api/access/register', { method: 'POST', body: { invitationId, presentCompanionIds } })
}

export function listRecentAccess(): Promise<RecentAccess[]> {
  return api<RecentAccess[]>('/api/access/recent')
}
