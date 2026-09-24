import type { Role } from '@/features/auth/types'
import type { LeadStatus } from './api'

export const LEAD_STATUS_LABELS: Record<LeadStatus, string> = {
  NEW: 'Novo',
  CONTACTED: 'Contatado',
  VISIT_SCHEDULED: 'Visita agendada',
  VISITED: 'Visitou',
  CANCELLED: 'Descartado',
}

export type LeadAction = 'CONTACT' | 'DISCARD' | 'REACTIVATE'

export const LEAD_ACTIONS: Record<LeadAction, { label: string; target: LeadStatus }> = {
  CONTACT: { label: 'Registrar contato', target: 'CONTACTED' },
  DISCARD: { label: 'Descartar Lead', target: 'CANCELLED' },
  REACTIVATE: { label: 'Reativar', target: 'NEW' },
}

/**
 * Ações manuais oferecidas na tela, espelhando as transições permitidas pelo backend (§7.2, D-063).
 * O backend continua sendo a autoridade: uma transição recusada volta como 409.
 */
export function leadActions(status: LeadStatus, role: Role): LeadAction[] {
  const actions: LeadAction[] = []
  if (status === 'NEW') actions.push('CONTACT')
  if (status !== 'CANCELLED') actions.push('DISCARD')
  if (status === 'CANCELLED' && role === 'ADMIN') actions.push('REACTIVATE')
  return actions
}

/**
 * "Agendar visita" (§16.2): Lead ativo sem visita agendada (D-040) e atribuído a um Prospector (RN03).
 * Quem abre o detalhe do Lead já pode escrever nele: ADMIN ou o dono atual (D-041).
 */
export function canScheduleVisit(status: LeadStatus, assigned: boolean): boolean {
  return assigned && (status === 'NEW' || status === 'CONTACTED' || status === 'VISITED')
}
