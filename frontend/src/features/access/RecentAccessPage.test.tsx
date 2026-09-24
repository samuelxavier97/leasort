import { screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { fakeRecent } from '@/test/accessFixtures'
import { me, mockFetch, renderApp } from '@/test/utils'

function renderRecent(rows: unknown[]) {
  mockFetch((_method, url) => {
    if (url === '/api/auth/me') return { body: me('GATE') }
    if (url === '/api/access/recent') return { body: rows }
  })
  return renderApp('/acessos')
}

// P7: campos e ordem da lista de acessos recentes (D-092).
describe('Acessos recentes', () => {
  it('mostra hora no fuso da operação, resultado, motivo, Lead, portaria e quem validou, na ordem da API', async () => {
    renderRecent([
      fakeRecent({ id: 'a-3', createdAt: '2026-09-24T14:30:00Z', leadName: 'Lead Três', validatedBy: 'Porteiro Um' }),
      fakeRecent({
        id: 'a-2',
        createdAt: '2026-09-24T13:10:00Z',
        result: 'DENIED',
        denialReason: 'WRONG_DATE',
        leadName: 'Lead Dois',
        validatedBy: 'Porteiro Dois',
      }),
      fakeRecent({ id: 'a-1', createdAt: '2026-09-24T12:05:00Z', result: 'DENIED', denialReason: 'INVALID_CODE', leadName: null }),
    ])

    const table = await screen.findByRole('table')
    expect(within(table).getAllByRole('columnheader').map((cell) => cell.textContent)).toEqual([
      'Hora',
      'Resultado',
      'Motivo',
      'Lead',
      'Portaria',
      'Validado por',
    ])
    const rows = within(table)
      .getAllByRole('row')
      .slice(1)
      .map((row) => within(row).getAllByRole('cell').map((cell) => cell.textContent))
    expect(rows).toEqual([
      ['11:30', 'Liberado', '—', 'Lead Três', 'PRINCIPAL', 'Porteiro Um'],
      ['10:10', 'Negado', 'Fora da data', 'Lead Dois', 'PRINCIPAL', 'Porteiro Dois'],
      ['09:05', 'Negado', 'Código inválido', '—', 'PRINCIPAL', 'Porteiro Fictício'],
    ])
  })

  it('sem acessos hoje, avisa', async () => {
    renderRecent([])

    expect(await screen.findByText('Nenhum acesso registrado hoje.')).toBeInTheDocument()
  })
})
