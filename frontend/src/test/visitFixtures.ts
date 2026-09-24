import type { Companion, Visit } from '@/features/visits/api'

/** Acompanhante fictício para testes de interface. */
export function fakeCompanion(overrides: Partial<Companion> = {}): Companion {
  return {
    id: 'c-1',
    name: 'Acompanhante Fictício',
    cpf: null,
    birthDate: '2012-05-20',
    relationship: 'CHILD',
    ...overrides,
  }
}

/** Visita fictícia para testes de interface. */
export function fakeVisit(overrides: Partial<Visit> = {}): Visit {
  return {
    id: 'v-1',
    lead: { id: 'lead-1', name: 'Lead Fictício', accessible: true },
    prospector: { id: 'p-1', name: 'Prospector Fictício' },
    scheduledDate: '2026-09-30',
    status: 'SCHEDULED',
    notes: null,
    hostNotes: null,
    companions: [],
    cancelledAt: null,
    createdAt: '2026-09-24T12:00:00Z',
    updatedAt: '2026-09-24T12:00:00Z',
    canEdit: true,
    ...overrides,
  }
}
