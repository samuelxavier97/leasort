import type { AccessByDay, AdminSummary, ProspectorSummary, VisitsByDay } from '@/features/dashboard/api'

export function fakeAdminSummary(overrides: Partial<AdminSummary> = {}): AdminSummary {
  return {
    from: '2026-08-26',
    to: '2026-09-24',
    today: '2026-09-24',
    totalLeads: 120,
    assignedLeads: 95,
    scheduledVisits: 14,
    visitsToday: 3,
    activeInvitations: 12,
    completedVisits: 40,
    noShows: 5,
    cancellations: 7,
    entries: 98,
    ...overrides,
  }
}

export function fakeProspectorSummary(overrides: Partial<ProspectorSummary> = {}): ProspectorSummary {
  return {
    from: '2026-08-26',
    to: '2026-09-24',
    today: '2026-09-24',
    myLeads: 22,
    scheduledVisits: 4,
    visitsToday: 1,
    activeInvitations: 4,
    completedVisits: 9,
    upcomingVisits: [
      { visitId: 'v-2', scheduledDate: '2026-09-25', leadName: 'Lead Amanhã', companionsCount: 1, canEdit: true },
      { visitId: 'v-1', scheduledDate: '2026-09-30', leadName: 'Lead Depois', companionsCount: 3, canEdit: false },
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

export function fakeAccessByDay(): AccessByDay {
  return {
    from: '2026-09-22',
    to: '2026-09-24',
    days: [
      { date: '2026-09-22', authorized: 2, denied: 1 },
      { date: '2026-09-23', authorized: 0, denied: 0 },
      { date: '2026-09-24', authorized: 1, denied: 4 },
    ],
  }
}
