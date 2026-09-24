import type { AccessResponse, RecentAccess } from '@/features/access/api'

/** Validação liberada fictícia, com dois acompanhantes. */
export function fakeAuthorized(overrides: Partial<AccessResponse> = {}): AccessResponse {
  return {
    result: 'AUTHORIZED',
    invitationId: 'i-1',
    leadName: 'Lead Fictício',
    scheduledDate: '2026-09-24',
    prospectorName: 'Prospector Fictício',
    companions: [
      { id: 'c-1', name: 'Acompanhante Um', relationship: 'SPOUSE' },
      { id: 'c-2', name: 'Acompanhante Dois', relationship: 'CHILD' },
    ],
    ...overrides,
  }
}

export function fakeRecent(overrides: Partial<RecentAccess> = {}): RecentAccess {
  return {
    id: 'a-1',
    createdAt: '2026-09-24T13:05:00Z',
    result: 'AUTHORIZED',
    denialReason: null,
    leadName: 'Lead Fictício',
    gate: 'PRINCIPAL',
    validatedBy: 'Porteiro Fictício',
    ...overrides,
  }
}
