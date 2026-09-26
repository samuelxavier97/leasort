import type { AuditEntry } from '@/features/audit/api'

export function fakeAuditEntry(overrides: Partial<AuditEntry> = {}): AuditEntry {
  return {
    id: 'a-1',
    createdAt: '2026-09-24T13:05:00Z',
    userId: 'u-1',
    userName: 'Administrador Fictício',
    action: 'LEAD_STATUS_CHANGED',
    entityType: 'LEAD',
    entityId: '0f8b2c1e-1111-2222-3333-444455556666',
    metadata: { from: 'NEW', to: 'CONTACTED' },
    ipAddress: '10.0.0.7',
    ...overrides,
  }
}
