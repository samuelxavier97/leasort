import { api } from '@/lib/api'
import type { Page } from '@/lib/page'

/** Linha da auditoria (D-102): "Sistema" quando não há usuário. */
export interface AuditEntry {
  id: string
  createdAt: string
  userId: string | null
  userName: string
  action: string
  entityType: string | null
  entityId: string | null
  metadata: Record<string, unknown> | null
  ipAddress: string | null
}

export interface AuditFilters {
  from: string
  to: string
  userId: string
  action: string
  entityType: string
  page: number
}

export const auditQueryKey = ['audit'] as const

export function auditUrl(filters: AuditFilters): string {
  const params = new URLSearchParams({ page: String(filters.page), size: '20' })
  if (filters.from) params.set('from', filters.from)
  if (filters.to) params.set('to', filters.to)
  if (filters.userId) params.set('userId', filters.userId)
  if (filters.action) params.set('action', filters.action)
  if (filters.entityType) params.set('entityType', filters.entityType)
  return `/api/audit?${params}`
}

export function searchAudit(filters: AuditFilters): Promise<Page<AuditEntry>> {
  return api<Page<AuditEntry>>(auditUrl(filters))
}
