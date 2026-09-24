import { z } from 'zod'
import { formatDate } from '@/lib/format'
import { maxVisitDate, operationToday } from '@/lib/date'

/** Limites do campo de data da visita: hoje até hoje + 12 meses, no fuso da operação (D-074, D-080). */
export function visitDateLimits() {
  return { min: operationToday(), max: maxVisitDate() }
}

/** Validação da data de agendamento e remarcação; o backend refaz a mesma checagem. */
export const visitDateSchema = z.string().superRefine((value, ctx) => {
  const { min, max } = visitDateLimits()
  if (value === '') {
    ctx.addIssue({ code: 'custom', message: 'Informe a data da visita.' })
  } else if (value < min) {
    ctx.addIssue({ code: 'custom', message: 'A data não pode ser anterior a hoje.' })
  } else if (value > max) {
    ctx.addIssue({ code: 'custom', message: `A data pode ser no máximo ${formatDate(max)} (12 meses a partir de hoje).` })
  }
})
