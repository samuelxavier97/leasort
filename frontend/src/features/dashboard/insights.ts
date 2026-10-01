import type { Greeting } from './api'

/** Textos e contas do dashboard (D-128), sem React, para os testes. */

const GREETINGS: Record<Greeting, string> = { MORNING: 'Bom dia', AFTERNOON: 'Boa tarde', EVENING: 'Boa noite' }

/** "Bom dia, Ana": o primeiro nome do usuário da sessão. */
export function greetingText(greeting: Greeting, fullName: string): string {
  const first = fullName.trim().split(/\s+/)[0]
  return first ? `${GREETINGS[greeting]}, ${first}` : GREETINGS[greeting]
}

const LONG_DATE = new Intl.DateTimeFormat('pt-BR', { weekday: 'long', day: 'numeric', month: 'long', timeZone: 'UTC' })

/** `2026-09-30` → "quarta-feira, 30 de setembro". A data vem do backend; o fuso do navegador não decide. */
export function longDate(isoDate: string): string {
  return LONG_DATE.format(new Date(`${isoDate}T12:00:00Z`))
}

/** "Hoje há 6 visitas; 2 já chegaram." — as que chegaram também contam (pedido na revisão). */
export function todaySentence(visits: number, arrived: number): string {
  if (visits === 0) {
    return 'Nenhuma visita para hoje.'
  }
  const total = visits === 1 ? 'Hoje há 1 visita' : `Hoje há ${visits} visitas`
  const done = arrived === 0 ? 'nenhuma chegou ainda' : arrived === 1 ? '1 já chegou' : `${arrived} já chegaram`
  return `${total}; ${done}.`
}

/** `AAAA-MM-DD` menos `days` dias, sem fuso: é aritmética de datas de calendário. */
export function minusDays(isoDate: string, days: number): string {
  const date = new Date(`${isoDate}T12:00:00Z`)
  date.setUTCDate(date.getUTCDate() - days)
  return date.toISOString().slice(0, 10)
}

/** Dias do período, com os dois extremos. */
export function periodDays(from: string, to: string): number {
  return Math.round((Date.parse(`${to}T12:00:00Z`) - Date.parse(`${from}T12:00:00Z`)) / 86_400_000) + 1
}

export const PRESETS = [7, 30, 90] as const
export type Preset = (typeof PRESETS)[number]

/** Os N dias até hoje, como o padrão da D-098 (hoje − N + 1 a hoje). */
export function presetPeriod(days: Preset, today: string): { from: string; to: string } {
  return { from: minusDays(today, days - 1), to: today }
}

/** Taxa de comparecimento: realizadas ÷ (realizadas + sem comparecimento); sem nenhuma, null. */
export function attendanceRate(completed: number, noShows: number): number | null {
  return completed + noShows === 0 ? null : completed / (completed + noShows)
}

export function formatPercent(rate: number | null): string {
  return rate === null ? '—' : `${Math.round(rate * 100)}%`
}

/** Sentido em que a mudança é boa: mais realizadas é bom, mais faltas é ruim (D-128). */
export type Better = 'higher' | 'lower'

export interface Comparison {
  /** `none`: sem base no período anterior. */
  direction: 'up' | 'down' | 'equal' | 'none'
  /** Se a mudança é boa, ruim ou neutra; define a cor, que nunca vem sozinha. */
  tone: 'good' | 'bad' | 'neutral'
  text: string
}

function tone(direction: 'up' | 'down', better: Better): 'good' | 'bad' {
  return (direction === 'up') === (better === 'higher') ? 'good' : 'bad'
}

/** Contagem: variação em %, com seta e texto. Período anterior zerado: "sem comparação". */
export function compareCount(current: number, previous: number, days: number, better: Better): Comparison {
  const reference = `nos ${days} dias anteriores`
  if (previous === 0) {
    return { direction: 'none', tone: 'neutral', text: 'sem comparação' }
  }
  if (current === previous) {
    return { direction: 'equal', tone: 'neutral', text: `= igual ${reference.replace('nos', 'aos')}` }
  }
  const direction = current > previous ? 'up' : 'down'
  const percent = Math.round((Math.abs(current - previous) / previous) * 100)
  const amount = percent === 0 ? 'menos de 1%' : `${percent}%`
  const text = direction === 'up' ? `▲ ${amount} a mais que ${reference}` : `▼ ${amount} a menos que ${reference}`
  return { direction, tone: tone(direction, better), text }
}

/** Taxa: variação em pontos percentuais, sobre as taxas já arredondadas que a tela mostra. */
export function compareRate(current: number | null, previous: number | null, days: number, better: Better): Comparison {
  const reference = `nos ${days} dias anteriores`
  if (current === null || previous === null) {
    return { direction: 'none', tone: 'neutral', text: 'sem comparação' }
  }
  const points = Math.round(current * 100) - Math.round(previous * 100)
  if (points === 0) {
    return { direction: 'equal', tone: 'neutral', text: `= igual ${reference.replace('nos', 'aos')}` }
  }
  const direction = points > 0 ? 'up' : 'down'
  const text = direction === 'up' ? `▲ ${points} p.p. a mais que ${reference}` : `▼ ${-points} p.p. a menos que ${reference}`
  return { direction, tone: tone(direction, better), text }
}
