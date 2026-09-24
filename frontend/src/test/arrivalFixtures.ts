import type { Arrival, VisitSheet } from '@/features/arrivals/api'

export function fakeArrival(overrides: Partial<Arrival> = {}): Arrival {
  return {
    visitId: 'v-1',
    entryAt: '2026-09-24T13:05:00Z',
    leadName: 'Lead Fictício',
    companionsPresent: 2,
    prospectorName: 'Prospector Fictício',
    ...overrides,
  }
}

export function fakeSheet(overrides: Partial<VisitSheet> = {}): VisitSheet {
  return {
    visitId: 'v-1',
    scheduledDate: '2026-09-24',
    entryAt: '2026-09-24T13:05:00Z',
    lead: { name: 'Lead Fictício', age: 34, phone: '11 90000-0000' },
    prospectorName: 'Prospector Fictício',
    presentCompanions: [
      { name: 'Acompanhante Um', relationship: 'SPOUSE', age: 33 },
      { name: 'Acompanhante Dois', relationship: 'CHILD', age: 1 },
    ],
    absentCompanions: [{ name: 'Acompanhante Três', relationship: 'FRIEND' }],
    hostNotes: 'Prefere conhecer as piscinas primeiro.',
    ...overrides,
  }
}
