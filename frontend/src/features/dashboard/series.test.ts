import { describe, expect, it } from 'vitest'
import { VISIT_SERIES } from './series'

describe('cores de "Visitas por dia" pelo significado (D-100 revisada)', () => {
  it('realizadas no azul, agendadas no azul claro, sem comparecimento no laranja e cancelamentos no cinza', () => {
    expect(Object.fromEntries(VISIT_SERIES.map((series) => [series.label, series.color]))).toEqual({
      Agendadas: '#86b6ef',
      Realizadas: '#2a78d6',
      'Sem comparecimento': '#eb6834',
      Cancelamentos: '#898781',
    })
  })

  it('nenhuma série de resultado ruim em verde', () => {
    for (const series of VISIT_SERIES.filter((item) => ['noShow', 'cancelled'].includes(item.key))) {
      const [r, g, b] = [1, 3, 5].map((index) => parseInt(series.color.slice(index, index + 2), 16))
      expect(g > r && g > b, series.label).toBe(false)
    }
  })
})
