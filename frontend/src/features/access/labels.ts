import { formatDate } from '@/lib/format'
import type { AccessResult, DenialReason } from './api'

export const ACCESS_RESULT_LABELS: Record<AccessResult, string> = {
  AUTHORIZED: 'Liberado',
  DENIED: 'Negado',
}

export const DENIAL_REASON_LABELS: Record<DenialReason, string> = {
  INVALID_CODE: 'Código inválido',
  CANCELLED: 'Convite cancelado',
  ALREADY_USED: 'Convite já utilizado',
  EXPIRED: 'Convite expirado',
  WRONG_DATE: 'Fora da data',
}

/** Motivo da negativa em linguagem clara (§16.5), com a data da visita quando o backend a envia. */
export function denialMessage(reason: DenialReason, scheduledDate?: string): string {
  switch (reason) {
    case 'INVALID_CODE':
      return 'Nenhum convite encontrado com este código. Confira o código e tente novamente.'
    case 'CANCELLED':
      return 'Este convite foi cancelado e não dá mais acesso.'
    case 'ALREADY_USED':
      return 'Este convite já foi utilizado. A entrada já está registrada.'
    case 'EXPIRED':
      return scheduledDate
        ? `Este convite expirou. A visita era em ${formatDate(scheduledDate)}.`
        : 'Este convite expirou.'
    case 'WRONG_DATE':
      return scheduledDate
        ? `Este convite não é para hoje. A visita está marcada para ${formatDate(scheduledDate)}.`
        : 'Este convite não é para hoje.'
  }
}

/** Motivo pelo qual a câmera não pôde ser usada (D-093). */
export type CameraProblem = 'insecure' | 'denied' | 'unavailable'

export const CAMERA_PROBLEM_MESSAGES: Record<CameraProblem, string> = {
  insecure: 'A câmera só funciona em conexão segura (HTTPS). Use a digitação do código.',
  denied: 'Sem permissão para usar a câmera. Libere o acesso nas configurações do navegador ou use a digitação do código.',
  unavailable: 'Nenhuma câmera disponível neste aparelho. Use a digitação do código.',
}
