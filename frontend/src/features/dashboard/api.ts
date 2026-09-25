import { api } from '@/lib/api'

/** Filtros do ADMIN (D-098, D-099): período e Prospector. O PROSPECTOR não envia nenhum. */
export interface DashboardParams {
  from?: string
  to?: string
  prospectorId?: string
}

export interface AdminSummary {
  from: string
  to: string
  today: string
  totalLeads: number
  assignedLeads: number
  scheduledVisits: number
  visitsToday: number
  activeInvitations: number
  completedVisits: number
  noShows: number
  cancellations: number
  /** Pessoas recebidas: o Lead mais os acompanhantes presentes (D-098). */
  entries: number
}

export interface UpcomingVisit {
  visitId: string
  scheduledDate: string
  leadName: string
  companionsCount: number
  canEdit: boolean
}

export interface ProspectorSummary {
  from: string
  to: string
  today: string
  myLeads: number
  scheduledVisits: number
  visitsToday: number
  activeInvitations: number
  completedVisits: number
  upcomingVisits: UpcomingVisit[]
}

export interface VisitsByDay {
  from: string
  to: string
  days: { date: string; scheduled: number; completed: number; noShow: number; cancelled: number }[]
}

export interface AccessByDay {
  from: string
  to: string
  days: { date: string; authorized: number; denied: number }[]
}

export const dashboardQueryKey = ['dashboard'] as const

function query(params: DashboardParams): string {
  const search = new URLSearchParams()
  if (params.from) search.set('from', params.from)
  if (params.to) search.set('to', params.to)
  if (params.prospectorId) search.set('prospectorId', params.prospectorId)
  const text = search.toString()
  return text ? `?${text}` : ''
}

export function getAdminSummary(params: DashboardParams): Promise<AdminSummary> {
  return api<AdminSummary>(`/api/dashboard/summary${query(params)}`)
}

/** Sem parâmetros: o backend usa os 30 dias até hoje e sempre o Prospector da sessão. */
export function getProspectorSummary(): Promise<ProspectorSummary> {
  return api<ProspectorSummary>('/api/dashboard/summary')
}

export function getVisitsByDay(params: DashboardParams): Promise<VisitsByDay> {
  return api<VisitsByDay>(`/api/dashboard/visits-by-day${query(params)}`)
}

export function getAccessByDay(params: DashboardParams): Promise<AccessByDay> {
  return api<AccessByDay>(`/api/dashboard/access-by-day${query(params)}`)
}
