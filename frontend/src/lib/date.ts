/**
 * "Hoje" no fuso da operação (D-080): o padrão de `APP_TIMEZONE`. Serve só para orientar os campos
 * de data; o backend recalcula e é a autoridade (D-074).
 */
export const OPERATION_TIMEZONE = 'America/Sao_Paulo'

/** Data `AAAA-MM-DD` de um instante no fuso da operação. */
export function operationDate(instant: Date): string {
  // en-CA formata como AAAA-MM-DD.
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: OPERATION_TIMEZONE,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(instant)
}

const timeFormat = new Intl.DateTimeFormat('pt-BR', { timeZone: OPERATION_TIMEZONE, hour: '2-digit', minute: '2-digit' })

/** Hora `HH:mm` de um instante ISO no fuso da operação. */
export function formatOperationTime(instant: string): string {
  return timeFormat.format(new Date(instant))
}

export function operationToday(now: Date = new Date()): string {
  return operationDate(now)
}

/** Soma meses a uma data `AAAA-MM-DD` como o `LocalDate.plusMonths` do Java: o dia é limitado ao fim do mês. */
export function plusMonths(isoDate: string, months: number): string {
  const [year, month, day] = isoDate.split('-').map(Number)
  const total = year * 12 + (month - 1) + months
  const targetYear = Math.floor(total / 12)
  const targetMonth = (total % 12) + 1
  const lastDay = new Date(Date.UTC(targetYear, targetMonth, 0)).getUTCDate()
  const pad = (value: number, length = 2) => String(value).padStart(length, '0')
  return `${pad(targetYear, 4)}-${pad(targetMonth)}-${pad(Math.min(day, lastDay))}`
}

/** Última data aceita para uma visita: hoje + 12 meses (D-074). */
export function maxVisitDate(now: Date = new Date()): string {
  return plusMonths(operationToday(now), 12)
}
