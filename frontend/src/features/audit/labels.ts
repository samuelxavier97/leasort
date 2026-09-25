import { ACCESS_RESULT_LABELS, DENIAL_REASON_LABELS } from '@/features/access/labels'
import { LEAD_STATUS_LABELS } from '@/features/leads/status'
import { ROLE_LABELS } from '@/app/navigation'
import { VISIT_STATUS_LABELS } from '@/features/visits/labels'
import { formatDate } from '@/lib/format'

/** Ações da §19, em português. */
export const AUDIT_ACTION_LABELS: Record<string, string> = {
  LOGIN: 'Login',
  LOGIN_FAILED: 'Login recusado',
  LOGOUT: 'Logout',
  PASSWORD_CHANGED: 'Senha alterada',
  PASSWORD_RESET: 'Senha redefinida',
  USER_CREATED: 'Usuário criado',
  USER_UPDATED: 'Usuário alterado',
  USER_STATUS_CHANGED: 'Usuário ativado ou desativado',
  LEAD_CREATED: 'Lead criado',
  LEAD_UPDATED: 'Lead alterado',
  LEAD_STATUS_CHANGED: 'Status do Lead alterado',
  LEAD_ASSIGNED: 'Lead atribuído',
  LEAD_IMPORTED: 'Leads importados',
  VISIT_CREATED: 'Visita agendada',
  VISIT_UPDATED: 'Visita alterada',
  VISIT_RESCHEDULED: 'Visita remarcada',
  VISIT_CANCELLED: 'Visita cancelada',
  VISIT_NO_SHOW: 'Não compareceu',
  INVITATION_CREATED: 'Convite criado',
  INVITATION_REISSUED: 'Convite reemitido',
  INVITATION_CANCELLED: 'Convite cancelado',
  INVITATION_EXPIRED: 'Convite expirado',
  ACCESS_VALIDATED: 'Entrada registrada',
  ACCESS_DENIED: 'Acesso negado',
  EXPORT_GENERATED: 'Exportação gerada',
}

/** Tipos de entidade aceitos pelo filtro (D-102). */
export const ENTITY_TYPE_LABELS: Record<string, string> = {
  USER: 'Usuário',
  PROSPECTOR: 'Prospector',
  LEAD: 'Lead',
  VISIT: 'Visita',
  INVITATION: 'Convite',
  ACCESS_RECORD: 'Acesso',
  EXPORT: 'Exportação',
}

const KEY_LABELS: Record<string, string> = {
  from: 'De',
  to: 'Para',
  cause: 'Causa',
  reason: 'Motivo',
  role: 'Perfil',
  active: 'Ativo',
  changedFields: 'Campos alterados',
  field: 'Campo',
  count: 'Quantidade',
  assigned: 'Atribuídos',
  assignedCount: 'Atribuídos',
  companions: 'Acompanhantes',
  companionsAdded: 'Acompanhantes incluídos',
  companionsRemoved: 'Acompanhantes removidos',
  companionsUpdated: 'Acompanhantes alterados',
  leadId: 'Lead',
  visitId: 'Visita',
  newVisitId: 'Nova visita',
  rescheduledFrom: 'Remarcada de',
  newInvitationId: 'Novo convite',
  reissuedFrom: 'Reemitido de',
  fromProspectorId: 'Prospector anterior',
  toProspectorId: 'Novo Prospector',
  access_record_id: 'Registro de acesso',
  source: 'Origem',
  type: 'Arquivo',
  filters: 'Filtros',
  status: 'Status',
  prospectorId: 'Prospector',
  rows: 'Linhas',
  message: 'Mensagem',
}

const KEY_ORDER = Object.keys(KEY_LABELS)

function keyRank(key: string): number {
  const index = KEY_ORDER.indexOf(key)
  return index < 0 ? KEY_ORDER.length : index
}

const EXPORT_LABELS: Record<string, string> = {
  LEADS: 'Leads',
  VISITS: 'Visitas',
  COMPANIONS: 'Acompanhantes',
  ACCESS: 'Acessos',
}

const CAUSE_LABELS: Record<string, string> = {
  VISIT_SCHEDULED: 'Visita agendada',
  VISIT_CANCELLED: 'Visita cancelada',
  VISIT_NO_SHOW: 'Não compareceu',
  ACCESS_REGISTERED: 'Entrada registrada',
  LEAD_DISCARDED: 'Lead descartado',
  VISIT_RESCHEDULED: 'Visita remarcada',
  RATE_LIMITED: 'Muitas tentativas',
  BOOTSTRAP: 'Criação inicial',
}

const VALUE_LABELS: Record<string, string> = {
  ...LEAD_STATUS_LABELS,
  ...VISIT_STATUS_LABELS,
  ...ACCESS_RESULT_LABELS,
  ...DENIAL_REASON_LABELS,
  ...ROLE_LABELS,
  ...CAUSE_LABELS,
}

function value(key: string, raw: unknown): string {
  if (raw === true) return 'Sim'
  if (raw === false) return 'Não'
  if (Array.isArray(raw)) return raw.map((item) => value(key, item)).join(', ')
  const text = String(raw)
  if (key === 'type' && EXPORT_LABELS[text]) return EXPORT_LABELS[text]
  if (/^\d{4}-\d{2}-\d{2}$/.test(text)) return formatDate(text)
  return VALUE_LABELS[text] ?? text
}

/**
 * Metadata legível (D-102): pares "Chave: valor" em português, sem os valores nulos; objetos aninhados (os
 * filtros de uma exportação) entram como "Filtros · Chave". Chaves desconhecidas aparecem como vieram.
 */
export function readableMetadata(metadata: Record<string, unknown> | null, prefix = ''): string[] {
  if (!metadata) return []
  // O jsonb do PostgreSQL reordena as chaves (mais curtas primeiro): a ordem de leitura vem de KEY_ORDER.
  const entries = Object.entries(metadata).sort(([a], [b]) => keyRank(a) - keyRank(b))
  return entries.flatMap(([key, raw]) => {
    if (raw === null || raw === undefined || raw === '') return []
    // Nos filtros de uma exportação, "to" é a data final do período.
    const label = prefix + (prefix && key === 'to' ? 'Até' : (KEY_LABELS[key] ?? key))
    if (typeof raw === 'object' && !Array.isArray(raw)) {
      return readableMetadata(raw as Record<string, unknown>, `${label} · `)
    }
    return [`${label}: ${value(key, raw)}`]
  })
}
