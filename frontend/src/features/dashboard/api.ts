import { api } from '@/lib/api'
import type { DenialReason } from '@/features/access/api'

/** Filtros do ADMIN (D-098, D-099): período e Prospector. O PROSPECTOR envia só o período (D-128). */
export interface DashboardParams {
  from?: string
  to?: string
  prospectorId?: string
}

/** Saudação calculada no backend pelo horário em `APP_TIMEZONE` (D-128). */
export type Greeting = 'MORNING' | 'AFTERNOON' | 'EVENING'

/** Visita de hoje (D-128): sem entrada, `arrivedAt` e `companionsPresent` são nulos. */
export interface TodayVisit {
  visitId: string
  leadName: string
  prospectorName: string
  companionsCount: number
  companionsPresent: number | null
  arrivedAt: string | null
}

export interface AdminSummary {
  from: string
  to: string
  today: string
  greeting: Greeting
  totalLeads: number
  assignedLeads: number
  scheduledVisits: number
  visitsToday: number
  arrivedToday: number
  activeInvitations: number
  completedVisits: number
  noShows: number
  cancellations: number
  /** Pessoas recebidas: o Lead mais os acompanhantes presentes (D-098). */
  entries: number
  previous: { from: string; to: string; completedVisits: number; noShows: number; cancellations: number; entries: number }
  todayVisits: TodayVisit[]
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
  greeting: Greeting
  myLeads: number
  scheduledVisits: number
  visitsToday: number
  arrivedToday: number
  activeInvitations: number
  completedVisits: number
  noShows: number
  previous: { from: string; to: string; completedVisits: number; noShows: number }
  upcomingVisits: UpcomingVisit[]
  todayVisits: TodayVisit[]
}

export interface VisitsByDay {
  from: string
  to: string
  days: { date: string; scheduled: number; completed: number; noShow: number; cancelled: number }[]
}

/** Negativas por motivo (D-129): os cinco motivos, do mais frequente ao menos. */
export interface Denials {
  from: string
  to: string
  total: number
  reasons: { reason: DenialReason; count: number }[]
}

/** Desempenho por Prospector (D-128), na ordem do backend: realizadas, taxa e nome. */
export interface ProspectorPerformance {
  from: string
  to: string
  prospectors: { prospectorId: string; name: string; completedVisits: number; noShows: number; attendanceRate: number }[]
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

/** Sem `prospectorId`: o backend usa sempre o Prospector da sessão. */
export function getProspectorSummary(params: Omit<DashboardParams, 'prospectorId'>): Promise<ProspectorSummary> {
  return api<ProspectorSummary>(`/api/dashboard/summary${query(params)}`)
}

export function getVisitsByDay(params: DashboardParams): Promise<VisitsByDay> {
  return api<VisitsByDay>(`/api/dashboard/visits-by-day${query(params)}`)
}

export function getDenials(params: DashboardParams): Promise<Denials> {
  return api<Denials>(`/api/dashboard/denials${query(params)}`)
}

/** Compara todos os Prospectores: sem o filtro de um deles. */
export function getProspectorPerformance(params: DashboardParams): Promise<ProspectorPerformance> {
  return api<ProspectorPerformance>(`/api/dashboard/prospector-performance${query({ from: params.from, to: params.to })}`)
}
