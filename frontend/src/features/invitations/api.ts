import { api, ApiError } from '@/lib/api'
import type { Page } from '@/lib/page'
import type { VisitStatus } from '@/features/visits/api'

export type InvitationStatus = 'ACTIVE' | 'USED' | 'CANCELLED' | 'EXPIRED'

/** Convite como a API entrega (D-086): sem CPF; o código só aparece nesta rota. */
export interface Invitation {
  id: string
  code: string
  /** `ABCDE-FGHJK` (§13). */
  formattedCode: string
  status: InvitationStatus
  expiresAt: string
  usedAt: string | null
  cancelledAt: string | null
  createdAt: string
  visit: { id: string; scheduledDate: string; status: VisitStatus; companionsCount: number; canEdit: boolean }
  lead: { id: string; name: string; accessible: boolean }
  prospector: { id: string; name: string }
  /** Escrita permitida, convite ACTIVE e visita SCHEDULED (D-084). */
  canReissue: boolean
}

export interface InvitationFilters {
  page: number
  status?: InvitationStatus | ''
  order?: 'asc' | 'desc'
}

export const invitationsQueryKey = ['invitations'] as const

export function listInvitations(filters: InvitationFilters): Promise<Page<Invitation>> {
  const params = new URLSearchParams({ page: String(filters.page) })
  if (filters.status) params.set('status', filters.status)
  if (filters.order) params.set('order', filters.order)
  return api<Page<Invitation>>(`/api/invitations?${params}`)
}

export function getInvitation(id: string): Promise<Invitation> {
  return api<Invitation>(`/api/invitations/${id}`)
}

export function reissueInvitation(id: string): Promise<Invitation> {
  return api<Invitation>(`/api/invitations/${id}/reissue`, { method: 'POST' })
}

/** PNG do QR (só `RSV:` + código, RN08). Só existe para convite ACTIVE (D-083). */
export async function getQrCode(id: string): Promise<Blob> {
  const response = await fetch(`/api/invitations/${id}/qr-code`, { credentials: 'same-origin' })
  if (!response.ok) {
    throw new ApiError(response.status, 'QR_UNAVAILABLE', 'QR indisponível.')
  }
  return response.blob()
}
