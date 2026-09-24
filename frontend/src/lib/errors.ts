import { ApiError } from './api'

/** Mensagens em português para os códigos estáveis do backend. */
const MESSAGES: Record<string, string> = {
  INVALID_CREDENTIALS: 'E-mail ou senha inválidos.',
  TOO_MANY_LOGIN_ATTEMPTS: 'Muitas tentativas de login. Aguarde alguns minutos e tente novamente.',
  UNAUTHENTICATED: 'Sua sessão expirou. Entre novamente.',
  ACCESS_DENIED: 'Você não tem permissão para esta ação.',
  PASSWORD_CHANGE_REQUIRED: 'É necessário trocar a senha antes de continuar.',
  CSRF_INVALID: 'Sua sessão foi atualizada. Tente novamente.',
  VALIDATION_ERROR: 'Verifique os dados informados.',
  INVALID_CURRENT_PASSWORD: 'Senha atual incorreta.',
  PASSWORD_UNCHANGED: 'A nova senha deve ser diferente da atual.',
  EMAIL_ALREADY_EXISTS: 'Já existe um usuário com este e-mail.',
  EMPLOYEE_CODE_ALREADY_EXISTS: 'Já existe um Prospector com este código.',
  ROLE_CHANGE_NOT_ALLOWED: 'Troca de perfil permitida apenas entre Administrador, Portaria e Anfitrião.',
  LAST_ADMIN: 'Não é possível remover o último administrador ativo.',
  USER_NOT_FOUND: 'Usuário não encontrado.',
  PROSPECTOR_NOT_FOUND: 'Prospector não encontrado.',
  PROSPECTOR_INACTIVE: 'O Prospector está inativo.',
  LEAD_NOT_FOUND: 'Lead não encontrado.',
  CPF_ALREADY_EXISTS: 'Já existe um Lead com este CPF.',
  CPF_CHANGE_NOT_ALLOWED: 'Este CPF só pode ser alterado pelo administrador.',
  INVALID_STATUS_TRANSITION: 'Esta mudança de status não é permitida para a situação atual do Lead.',
  FILE_TOO_LARGE: 'O arquivo é maior que o limite de 5 MB.',
  VISIT_NOT_FOUND: 'Visita não encontrada.',
  LEAD_INACTIVE: 'Este Lead foi descartado. Reative-o antes de agendar uma visita.',
  LEAD_NOT_ASSIGNED: 'Este Lead não tem Prospector. Atribua um Prospector antes de agendar.',
  VISIT_ALREADY_SCHEDULED: 'Este Lead já tem uma visita agendada.',
  SCHEDULED_DATE_IN_PAST: 'A data da visita não pode ser anterior a hoje.',
  SCHEDULED_DATE_TOO_FAR: 'A data da visita pode ser no máximo 12 meses a partir de hoje.',
  SAME_DATE: 'Escolha uma data diferente da atual.',
  TOO_MANY_COMPANIONS: 'A visita excede o número máximo de acompanhantes.',
  VISIT_NOT_EDITABLE: 'Esta visita não pode mais ser alterada.',
  COMPANION_NOT_FOUND: 'Um dos acompanhantes não pertence mais a esta visita. Recarregue a página.',
  INVALID_VISIT_TRANSITION: 'Esta ação não é permitida para a situação atual da visita.',
  INVITATION_NOT_FOUND: 'Convite não encontrado.',
  INVITATION_NOT_ACTIVE: 'Este convite não está mais ativo.',
  INVITATION_CODE_CONFLICT: 'Não foi possível gerar o código do convite. Tente novamente.',
  TOO_MANY_VALIDATIONS: 'Muitas validações em pouco tempo. Aguarde um minuto e tente novamente.',
  NOT_FOUND: 'Recurso não encontrado.',
}

/** Códigos cujo detalhe vindo do backend já é a melhor mensagem em português. */
const DETAIL_CODES = new Set([
  'INVALID_HEADER',
  'EMPTY_FILE',
  'TOO_MANY_ROWS',
  'INVALID_ENCODING',
  'IMPORT_REJECTED',
  // Traz o limite configurado em APP_MAX_COMPANIONS, que o frontend não fixa (D-075).
  'TOO_MANY_COMPANIONS',
])

export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    // Para validação, o backend já devolve um detalhe específico em português.
    if (error.code === 'VALIDATION_ERROR' && error.message && error.message !== 'Dados inválidos.') {
      return error.message
    }
    if (DETAIL_CODES.has(error.code) && error.message) {
      return error.message
    }
    return MESSAGES[error.code] ?? 'Não foi possível concluir a operação.'
  }
  return 'Falha de comunicação com o servidor.'
}
