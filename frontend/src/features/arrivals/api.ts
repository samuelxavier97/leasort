import { api } from '@/lib/api'
import type { Relationship } from '@/features/visits/api'

/** Chegada (D-095): entrada liberada, sem CPF nem outro dado além da lista. */
export interface Arrival {
  visitId: string
  entryAt: string
  leadName: string
  companionsPresent: number
  prospectorName: string
}

export interface Arrivals {
  /** Dia usado pelo backend em APP_TIMEZONE. */
  date: string
  arrivals: Arrival[]
}

/** Ficha da visita (D-096): idades na data da visita; nunca CPF, e-mail, nascimento ou notas internas. */
export interface VisitSheet {
  visitId: string
  scheduledDate: string
  entryAt: string
  lead: { name: string; age: number | null; phone: string | null }
  prospectorName: string
  presentCompanions: { name: string; relationship: Relationship; age: number }[]
  absentCompanions: { name: string; relationship: Relationship }[]
  hostNotes: string | null
}

export const arrivalsQueryKey = ['arrivals'] as const

/** Sem data, o backend usa hoje: é o que HOST e PROSPECTOR pedem, inclusive depois da meia-noite. */
export function listArrivals(date?: string): Promise<Arrivals> {
  return api<Arrivals>(date ? `/api/arrivals?date=${date}` : '/api/arrivals')
}

export function getVisitSheet(visitId: string): Promise<VisitSheet> {
  return api<VisitSheet>(`/api/visits/${visitId}/sheet`)
}
