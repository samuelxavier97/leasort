import type { InvitationStatus } from './api'

export const INVITATION_STATUS_LABELS: Record<InvitationStatus, string> = {
  ACTIVE: 'Ativo',
  USED: 'Utilizado',
  CANCELLED: 'Cancelado',
  EXPIRED: 'Expirado',
}
