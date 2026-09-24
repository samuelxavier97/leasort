import { api } from '@/lib/api'
import type { Page } from '@/lib/page'

export type VisitStatus = 'SCHEDULED' | 'COMPLETED' | 'CANCELLED' | 'NO_SHOW'

export type Relationship =
  | 'SPOUSE'
  | 'CHILD'
  | 'FATHER'
  | 'MOTHER'
  | 'SIBLING'
  | 'GRANDPARENT'
  | 'GRANDCHILD'
  | 'FRIEND'
  | 'OTHER'

/** CPF já vem pronto do backend: completo para o ADMIN, mascarado para o PROSPECTOR (D-060, D-075). */
export interface Companion {
  id: string
  name: string
  cpf: string | null
  birthDate: string
  relationship: Relationship
}

export interface Visit {
  id: string
  /** `accessible`: quem vê pode abrir o Lead, pela regra de carteira do backend (D-078). */
  lead: { id: string; name: string; accessible: boolean }
  prospector: { id: string; name: string }
  scheduledDate: string
  status: VisitStatus
  notes: string | null
  hostNotes: string | null
  companions: Companion[]
  cancelledAt: string | null
  createdAt: string
  updatedAt: string
  /** Calculado no backend: escrita permitida e visita `SCHEDULED` (D-078). */
  canEdit: boolean
}

export interface CompanionInput {
  /** Presente só para acompanhantes já existentes; o backend casa por id (D-075). */
  id?: string
  name: string
  /** `null` mantém o CPF atual; `''` remove (só ADMIN). */
  cpf: string | null
  birthDate: string
  relationship: Relationship
}

export interface CreateVisitInput {
  leadId: string
  scheduledDate: string
  notes: string | null
  hostNotes: string | null
  companions: CompanionInput[]
}

export interface UpdateVisitInput {
  notes: string | null
  hostNotes: string | null
  companions: CompanionInput[]
}

export interface VisitFilters {
  page: number
  status?: VisitStatus | ''
  from?: string
  leadId?: string
  prospectorId?: string
  order?: 'asc' | 'desc'
  /** Tudo o que não está na Agenda (D-079). */
  scope?: 'history'
  size?: number
}

export const visitsQueryKey = ['visits'] as const

export function listVisits(filters: VisitFilters): Promise<Page<Visit>> {
  const params = new URLSearchParams({ page: String(filters.page) })
  if (filters.size) params.set('size', String(filters.size))
  if (filters.status) params.set('status', filters.status)
  if (filters.from) params.set('from', filters.from)
  if (filters.leadId) params.set('leadId', filters.leadId)
  if (filters.prospectorId) params.set('prospectorId', filters.prospectorId)
  if (filters.order) params.set('order', filters.order)
  if (filters.scope) params.set('scope', filters.scope)
  return api<Page<Visit>>(`/api/visits?${params}`)
}

export function getVisit(id: string): Promise<Visit> {
  return api<Visit>(`/api/visits/${id}`)
}

export function createVisit(input: CreateVisitInput): Promise<Visit> {
  return api<Visit>('/api/visits', { method: 'POST', body: input })
}

export function updateVisit(id: string, input: UpdateVisitInput): Promise<Visit> {
  return api<Visit>(`/api/visits/${id}`, { method: 'PUT', body: input })
}

export function rescheduleVisit(id: string, scheduledDate: string): Promise<Visit> {
  return api<Visit>(`/api/visits/${id}/reschedule`, { method: 'POST', body: { scheduledDate } })
}

export function cancelVisit(id: string): Promise<Visit> {
  return api<Visit>(`/api/visits/${id}/cancel`, { method: 'PATCH' })
}
