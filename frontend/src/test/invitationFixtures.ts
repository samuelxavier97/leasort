import type { Invitation } from '@/features/invitations/api'

/** Convite fictício; o código é gerado na hora, sem valor fixo no repositório. */
export function fakeInvitation(overrides: Partial<Invitation> = {}): Invitation {
  const code = fakeCode()
  return {
    id: 'i-1',
    code,
    formattedCode: `${code.slice(0, 5)}-${code.slice(5)}`,
    status: 'ACTIVE',
    expiresAt: '2026-10-01T03:00:00Z',
    usedAt: null,
    cancelledAt: null,
    createdAt: '2026-09-24T12:00:00Z',
    visit: { id: 'v-1', scheduledDate: '2026-09-30', status: 'SCHEDULED', companionsCount: 2, canEdit: true },
    lead: { id: 'lead-1', name: 'Lead Fictício', accessible: true },
    prospector: { id: 'p-1', name: 'Prospector Fictício' },
    canReissue: true,
    ...overrides,
  }
}

export function fakeCode(): string {
  const alphabet = '0123456789ABCDEFGHJKMNPQRSTVWXYZ'
  return Array.from({ length: 10 }, () => alphabet[Math.floor(Math.random() * alphabet.length)]).join('')
}
