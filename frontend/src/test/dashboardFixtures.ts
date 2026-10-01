import type { AdminSummary, Denials, ProspectorPerformance, ProspectorSummary, VisitsByDay } from '@/features/dashboard/api'

export function fakeAdminSummary(overrides: Partial<AdminSummary> = {}): AdminSummary {
  return {
    from: '2026-08-26',
    to: '2026-09-24',
    today: '2026-09-24',
    greeting: 'AFTERNOON',
    totalLeads: 120,
    assignedLeads: 95,
    scheduledVisits: 14,
    visitsToday: 3,
    arrivedToday: 1,
    activeInvitations: 12,
    completedVisits: 40,
    noShows: 10,
    cancellations: 7,
    entries: 98,
    previous: { from: '2026-07-27', to: '2026-08-25', completedVisits: 32, noShows: 12, cancellations: 7, entries: 0 },
    todayVisits: [
      { visitId: 't-1', leadName: 'Lead Chegou', prospectorName: 'Ana Prospectora', companionsCount: 3, companionsPresent: 2, arrivedAt: '2026-09-24T12:40:00Z' },
      { visitId: 't-2', leadName: 'Lead Agendado', prospectorName: 'Bia Prospectora', companionsCount: 1, companionsPresent: null, arrivedAt: null },
      { visitId: 't-3', leadName: 'Lead Sozinho', prospectorName: 'Bia Prospectora', companionsCount: 0, companionsPresent: null, arrivedAt: null },
    ],
    ...overrides,
  }
}

export function fakeProspectorSummary(overrides: Partial<ProspectorSummary> = {}): ProspectorSummary {
  return {
    from: '2026-08-26',
    to: '2026-09-24',
    today: '2026-09-24',
    greeting: 'MORNING',
    myLeads: 22,
    scheduledVisits: 4,
    visitsToday: 1,
    arrivedToday: 0,
    activeInvitations: 4,
    completedVisits: 9,
    noShows: 1,
    previous: { from: '2026-07-27', to: '2026-08-25', completedVisits: 10, noShows: 0 },
    upcomingVisits: [
      { visitId: 'v-2', scheduledDate: '2026-09-25', leadName: 'Lead Amanhã', companionsCount: 1, canEdit: true },
      { visitId: 'v-1', scheduledDate: '2026-09-30', leadName: 'Lead Depois', companionsCount: 3, canEdit: false },
    ],
    todayVisits: [
      { visitId: 't-9', leadName: 'Lead de Hoje', prospectorName: 'Usuário PROSPECTOR', companionsCount: 2, companionsPresent: null, arrivedAt: null },
    ],
    ...overrides,
  }
}

export function fakeVisitsByDay(): VisitsByDay {
  return {
    from: '2026-09-22',
    to: '2026-09-24',
    days: [
      { date: '2026-09-22', scheduled: 0, completed: 2, noShow: 1, cancelled: 0 },
      { date: '2026-09-23', scheduled: 0, completed: 0, noShow: 0, cancelled: 0 },
      { date: '2026-09-24', scheduled: 3, completed: 1, noShow: 0, cancelled: 1 },
    ],
  }
}

export function fakeDenials(overrides: Partial<Denials> = {}): Denials {
  return {
    from: '2026-08-26',
    to: '2026-09-24',
    total: 30,
    reasons: [
      { reason: 'INVALID_CODE', count: 16 },
      { reason: 'ALREADY_USED', count: 7 },
      { reason: 'WRONG_DATE', count: 4 },
      { reason: 'CANCELLED', count: 2 },
      { reason: 'EXPIRED', count: 1 },
    ],
    ...overrides,
  }
}

export function fakePerformance(): ProspectorPerformance {
  return {
    from: '2026-08-26',
    to: '2026-09-24',
    prospectors: [
      { prospectorId: 'p-2', name: 'Bia Prospectora', completedVisits: 30, noShows: 10, attendanceRate: 0.75 },
      { prospectorId: 'p-1', name: 'Ana Prospectora', completedVisits: 10, noShows: 0, attendanceRate: 1 },
    ],
  }
}
