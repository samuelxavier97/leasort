import { randomInt } from 'node:crypto'

/** Fuso da operação (APP_TIMEZONE padrão); o E2E roda o backend com ele. */
export const OPERATION_TIMEZONE = 'America/Sao_Paulo'

/** Sufixo curto e único por teste, para nomes, e-mails e códigos de funcionário. */
export function uniqueSuffix(): string {
  const alphabet = 'ABCDEFGHJKMNPQRSTVWXYZ23456789'
  return Array.from({ length: 6 }, () => alphabet[randomInt(alphabet.length)]).join('')
}

/** CPF fictício válido (dígitos verificadores calculados), só com dígitos. */
export function fakeCpf(): string {
  let base: number[]
  do {
    base = Array.from({ length: 9 }, () => randomInt(10))
  } while (base.every((digit) => digit === base[0]))
  const check = (digits: number[]) => {
    const sum = digits.reduce((total, digit, index) => total + digit * (digits.length + 1 - index), 0)
    const rest = (sum * 10) % 11
    return rest === 10 ? 0 : rest
  }
  const first = check(base)
  const second = check([...base, first])
  return [...base, first, second].join('')
}

export function formatCpf(digits: string): string {
  return `${digits.slice(0, 3)}.${digits.slice(3, 6)}.${digits.slice(6, 9)}-${digits.slice(9)}`
}

/** Hoje (ou hoje + dias) no fuso da operação, como YYYY-MM-DD. */
export function operationDate(plusDays = 0): string {
  const today = new Intl.DateTimeFormat('en-CA', { timeZone: OPERATION_TIMEZONE }).format(new Date())
  const date = new Date(`${today}T12:00:00Z`)
  date.setUTCDate(date.getUTCDate() + plusDays)
  return date.toISOString().slice(0, 10)
}

/** YYYY-MM-DD → DD/MM/AAAA, o formato da interface. */
export function displayDate(isoDate: string): string {
  const [year, month, day] = isoDate.split('-')
  return `${day}/${month}/${year}`
}

/** Idade completa em anos na data de hoje da operação, como a ficha calcula (D-096). */
export function ageOn(birthDate: string, onDate = operationDate()): number {
  const [by, bm, bd] = birthDate.split('-').map(Number)
  const [ty, tm, td] = onDate.split('-').map(Number)
  return ty - by - (tm < bm || (tm === bm && td < bd) ? 1 : 0)
}
