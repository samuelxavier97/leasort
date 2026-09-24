import { api, apiUpload } from '@/lib/api'
import type { Page } from '@/lib/page'

export type LeadStatus = 'NEW' | 'CONTACTED' | 'VISIT_SCHEDULED' | 'VISITED' | 'CANCELLED'

/** CPF já vem pronto do backend: completo para o ADMIN, mascarado para o PROSPECTOR (D-060). */
export interface Lead {
  id: string
  name: string
  cpf: string | null
  phone: string | null
  email: string | null
  birthDate: string | null
  notes: string | null
  status: LeadStatus
  prospector: { id: string; name: string } | null
  createdAt: string
  updatedAt: string
}

export interface LeadInput {
  name: string
  /** `null` mantém o CPF atual; `''` remove (só ADMIN) — D-061. */
  cpf: string | null
  phone: string | null
  email: string | null
  birthDate: string | null
  notes: string | null
  prospectorId?: string | null
}

export interface LeadFilters {
  page: number
  q?: string
  status?: LeadStatus | ''
  prospectorId?: string
  unassigned?: boolean
}

export interface ImportResult {
  imported: number
  totalRows: number
}

export interface ImportRowError {
  line: number
  column: string | null
  code: string
  message: string
}

export const leadsQueryKey = ['leads'] as const
export const LEAD_IMPORT_TEMPLATE_URL = '/api/leads/import/template'

export function listLeads(filters: LeadFilters): Promise<Page<Lead>> {
  const params = new URLSearchParams({ page: String(filters.page) })
  if (filters.q) params.set('q', filters.q)
  if (filters.status) params.set('status', filters.status)
  if (filters.prospectorId) params.set('prospectorId', filters.prospectorId)
  if (filters.unassigned) params.set('unassigned', 'true')
  return api<Page<Lead>>(`/api/leads?${params}`)
}

export function getLead(id: string): Promise<Lead> {
  return api<Lead>(`/api/leads/${id}`)
}

export function createLead(input: LeadInput): Promise<Lead> {
  return api<Lead>('/api/leads', { method: 'POST', body: input })
}

export function updateLead(id: string, input: LeadInput): Promise<Lead> {
  return api<Lead>(`/api/leads/${id}`, { method: 'PUT', body: input })
}

export function changeLeadStatus(id: string, status: LeadStatus): Promise<Lead> {
  return api<Lead>(`/api/leads/${id}/status`, { method: 'PATCH', body: { status } })
}

export function assignLeads(leadIds: string[], prospectorId: string): Promise<{ assigned: number }> {
  return api('/api/leads/assign', { method: 'PATCH', body: { leadIds, prospectorId } })
}

export function importLeads(file: File): Promise<ImportResult> {
  return apiUpload<ImportResult>('/api/leads/import', file)
}
