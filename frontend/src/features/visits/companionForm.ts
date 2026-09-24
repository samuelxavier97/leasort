import { z } from 'zod'
import type { Role } from '@/features/auth/types'
import { isValidCpf } from '@/lib/cpf'
import { operationToday } from '@/lib/date'
import type { Companion, CompanionInput, Relationship } from './api'
import { RELATIONSHIP_LABELS } from './labels'

const RELATIONSHIPS = Object.keys(RELATIONSHIP_LABELS) as Relationship[]

/**
 * Acompanhante no formulário. `companionId` guarda o id de um acompanhante existente (o `id` é usado
 * pelo useFieldArray); `lockedCpf` é o CPF mascarado que o PROSPECTOR vê e não pode alterar (D-075).
 */
export const companionSchema = z.object({
  companionId: z.string().optional(),
  lockedCpf: z.string().nullable(),
  hadCpf: z.boolean(),
  name: z.string().trim().min(1, 'Informe o nome.').max(120, 'Nome muito longo.'),
  cpf: z.string().trim().refine((value) => value === '' || isValidCpf(value), 'CPF inválido.'),
  birthDate: z
    .string()
    .min(1, 'Informe a data de nascimento.')
    .refine((value) => value >= '1900-01-01' && value <= operationToday(), 'Data de nascimento inválida.'),
  // String no formulário ('' = não escolhido); só valores da lista passam.
  relationship: z.string().refine((value) => (RELATIONSHIPS as string[]).includes(value), 'Informe o parentesco.'),
})

export type CompanionForm = z.infer<typeof companionSchema>

export function emptyCompanion(): CompanionForm {
  return { lockedCpf: null, hadCpf: false, name: '', cpf: '', birthDate: '', relationship: '' }
}

/** Acompanhante existente no formulário de edição. Para quem não é ADMIN, CPF existente fica só leitura. */
export function companionFromVisit(companion: Companion, role: Role): CompanionForm {
  const locked = role !== 'ADMIN' && companion.cpf !== null
  return {
    companionId: companion.id,
    lockedCpf: locked ? companion.cpf : null,
    hadCpf: companion.cpf !== null,
    name: companion.name,
    cpf: locked ? '' : (companion.cpf ?? ''),
    birthDate: companion.birthDate,
    relationship: companion.relationship,
  }
}

/**
 * Corpo enviado ao backend: id só para os existentes; CPF bloqueado vai como `null` (mantém);
 * CPF apagado pelo ADMIN num acompanhante que tinha CPF vai como `''` (remove).
 */
export function companionInput(form: CompanionForm): CompanionInput {
  let cpf: string | null
  if (form.lockedCpf !== null) {
    cpf = null
  } else if (form.cpf) {
    cpf = form.cpf
  } else {
    cpf = form.hadCpf ? '' : null
  }
  const input: CompanionInput = {
    name: form.name,
    cpf,
    birthDate: form.birthDate,
    relationship: form.relationship as Relationship,
  }
  if (form.companionId) {
    input.id = form.companionId
  }
  return input
}
