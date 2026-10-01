import { describe, expect, it } from 'vitest'
import {
  attendanceRate,
  compareCount,
  compareRate,
  formatPercent,
  greetingText,
  longDate,
  periodDays,
  presetPeriod,
  todaySentence,
} from './insights'

describe('textos do dashboard (D-128)', () => {
  it('saudação com o primeiro nome', () => {
    expect(greetingText('MORNING', 'Ana Paula Ribeiro')).toBe('Bom dia, Ana')
    expect(greetingText('AFTERNOON', '  Bia  ')).toBe('Boa tarde, Bia')
    expect(greetingText('EVENING', '')).toBe('Boa noite')
  })

  it('data por extenso, sem depender do fuso do navegador', () => {
    expect(longDate('2026-09-30')).toBe('quarta-feira, 30 de setembro')
    expect(longDate('2026-01-01')).toBe('quinta-feira, 1 de janeiro')
  })

  it('resumo de hoje: as que chegaram também contam', () => {
    expect(todaySentence(6, 2)).toBe('Hoje há 6 visitas; 2 já chegaram.')
    expect(todaySentence(1, 1)).toBe('Hoje há 1 visita; 1 já chegou.')
    expect(todaySentence(3, 0)).toBe('Hoje há 3 visitas; nenhuma chegou ainda.')
    expect(todaySentence(0, 0)).toBe('Nenhuma visita para hoje.')
  })

  it('períodos de 7, 30 e 90 dias até hoje, com os dois extremos', () => {
    expect(presetPeriod(7, '2026-09-24')).toEqual({ from: '2026-09-18', to: '2026-09-24' })
    expect(presetPeriod(30, '2026-03-01')).toEqual({ from: '2026-01-31', to: '2026-03-01' })
    expect(periodDays('2026-06-27', '2026-09-24')).toBe(90)
  })
})

describe('comparação com o período anterior (D-128)', () => {
  it('contagens em %, com o tom pelo sentido bom', () => {
    expect(compareCount(40, 32, 30, 'higher')).toEqual({ direction: 'up', tone: 'good', text: '▲ 25% a mais que nos 30 dias anteriores' })
    expect(compareCount(40, 32, 30, 'lower')).toMatchObject({ direction: 'up', tone: 'bad' })
    expect(compareCount(10, 12, 7, 'lower')).toEqual({ direction: 'down', tone: 'good', text: '▼ 17% a menos que nos 7 dias anteriores' })
    expect(compareCount(10, 12, 7, 'higher')).toMatchObject({ tone: 'bad' })
    expect(compareCount(7, 7, 30, 'higher')).toEqual({ direction: 'equal', tone: 'neutral', text: '= igual aos 30 dias anteriores' })
    expect(compareCount(1001, 1000, 30, 'higher').text).toBe('▲ menos de 1% a mais que nos 30 dias anteriores')
  })

  it('sem base no período anterior: "sem comparação"', () => {
    expect(compareCount(5, 0, 30, 'higher')).toEqual({ direction: 'none', tone: 'neutral', text: 'sem comparação' })
    expect(compareCount(0, 0, 30, 'higher').text).toBe('sem comparação')
    expect(compareRate(0.5, null, 30, 'higher').text).toBe('sem comparação')
    expect(compareRate(null, 0.5, 30, 'higher').text).toBe('sem comparação')
  })

  it('taxas em pontos percentuais, sobre as taxas mostradas', () => {
    expect(compareRate(0.8, 32 / 44, 30, 'higher')).toEqual({ direction: 'up', tone: 'good', text: '▲ 7 p.p. a mais que nos 30 dias anteriores' })
    expect(compareRate(0.62, 0.66, 30, 'higher')).toEqual({ direction: 'down', tone: 'bad', text: '▼ 4 p.p. a menos que nos 30 dias anteriores' })
    // 0,801 e 0,799 aparecem como 80% e 80%: igual.
    expect(compareRate(0.801, 0.799, 30, 'higher').direction).toBe('equal')
  })

  it('taxa de comparecimento: realizadas ÷ (realizadas + sem comparecimento)', () => {
    expect(attendanceRate(40, 10)).toBe(0.8)
    expect(attendanceRate(0, 0)).toBeNull()
    expect(formatPercent(0.625)).toBe('63%')
    expect(formatPercent(null)).toBe('—')
  })
})
