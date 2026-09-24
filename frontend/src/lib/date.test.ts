import { describe, expect, it } from 'vitest'
import { maxVisitDate, operationToday, plusMonths } from './date'

describe('datas no fuso da operação', () => {
  it('hoje é calculado em America/Sao_Paulo, não em UTC', () => {
    expect(operationToday(new Date('2026-03-10T02:59:59Z'))).toBe('2026-03-09')
    expect(operationToday(new Date('2026-03-10T03:00:00Z'))).toBe('2026-03-10')
  })

  it('soma meses limitando o dia ao fim do mês, como o backend', () => {
    expect(plusMonths('2026-09-24', 12)).toBe('2027-09-24')
    expect(plusMonths('2028-02-29', 12)).toBe('2029-02-28')
    expect(plusMonths('2026-11-30', 3)).toBe('2027-02-28')
  })

  it('a data máxima de visita é hoje + 12 meses no fuso da operação', () => {
    expect(maxVisitDate(new Date('2026-03-10T02:00:00Z'))).toBe('2027-03-09')
  })
})
