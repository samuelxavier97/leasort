import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { EmptyState, LoadError, Notice, PageLoading } from '@/components/PageState'
import { fakeRecent } from '@/test/accessFixtures'
import { fakeLead, page } from '@/test/leadFixtures'
import { me, mockFetch, problem, renderApp } from '@/test/utils'
import { fakeVisit } from '@/test/visitFixtures'

// Padrões de tela da Fase 12 (D-122).
describe('PageState', () => {
  it('carregando, vazio, erro com "Tentar de novo" e avisos em cores fixas', async () => {
    const onRetry = vi.fn()
    const user = userEvent.setup()
    render(
      <>
        <PageLoading />
        <EmptyState>Nada por aqui.</EmptyState>
        <LoadError error={new Error('x')} onRetry={onRetry} />
        <Notice tone="warning">Atenção.</Notice>
        <Notice tone="success">Pronto.</Notice>
      </>,
    )

    expect(screen.getAllByRole('status')[0]).toHaveTextContent('Carregando...')
    expect(screen.getByText('Nada por aqui.')).toHaveClass('border-dashed')
    await user.click(within(screen.getByRole('alert')).getByRole('button', { name: 'Tentar de novo' }))
    expect(onRetry).toHaveBeenCalledOnce()
    expect(screen.getByText('Atenção.').parentElement).toHaveClass('bg-amber-50')
    expect(screen.getByText('Pronto.').parentElement).toHaveClass('bg-green-50')
    for (const text of ['Atenção.', 'Pronto.']) {
      expect(screen.getByText(text).parentElement!.className).not.toMatch(/primary/)
    }
  })
})

describe('Listas: vazio fora da tabela e erro com nova tentativa', () => {
  it('lista vazia mostra o aviso sem tabela', async () => {
    mockFetch((_method, url) => {
      if (url === '/api/auth/me') return { body: me('ADMIN') }
      if (url.startsWith('/api/leads?')) return { body: page([]) }
      if (url.startsWith('/api/prospectors')) return { body: page([]) }
    })
    renderApp('/leads')

    expect(await screen.findByText('Nenhum Lead encontrado.')).toBeInTheDocument()
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
  })

  it('erro ao carregar oferece "Tentar de novo", que refaz a consulta', async () => {
    let calls = 0
    mockFetch((_method, url) => {
      if (url === '/api/auth/me') return { body: me('ADMIN') }
      if (url.startsWith('/api/leads?')) {
        calls += 1
        return calls === 1 ? problem(503, 'UNAVAILABLE') : { body: page([fakeLead()]) }
      }
      if (url.startsWith('/api/prospectors')) return { body: page([]) }
    })
    const user = userEvent.setup()
    renderApp('/leads')

    const alert = await screen.findByRole('alert')
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
    await user.click(within(alert).getByRole('button', { name: 'Tentar de novo' }))

    expect(await screen.findByRole('table')).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    expect(calls).toBe(2)
  })
})

describe('Tabela em cartões no celular', () => {
  it('Acessos Recentes: cada célula leva o rótulo da coluna', async () => {
    mockFetch((_method, url) => {
      if (url === '/api/auth/me') return { body: me('GATE') }
      if (url === '/api/access/recent') return { body: [fakeRecent({ leadName: 'Lead Um' })] }
    })
    renderApp('/acessos')

    const table = await screen.findByRole('table')
    expect(table).toHaveClass('table-cards')
    const headers = within(table)
      .getAllByRole('columnheader')
      .map((cell) => cell.textContent)
    const labels = within(table)
      .getAllByRole('cell')
      .map((cell) => cell.getAttribute('data-label'))
    expect(labels).toEqual(headers)
  })
})

describe('Ações destrutivas em vermelho', () => {
  it('"Cancelar visita" e a confirmação usam a cor de perigo, não a do cliente', async () => {
    mockFetch((method, url) => {
      if (url === '/api/auth/me') return { body: me('PROSPECTOR') }
      if (method === 'GET' && url === '/api/visits/v-1') return { body: fakeVisit() }
      if (method === 'GET' && url.startsWith('/api/visits?')) return { body: page([]) }
    })
    const user = userEvent.setup()
    renderApp('/visitas/v-1')

    const trigger = await screen.findByRole('button', { name: 'Cancelar visita' })
    expect(trigger).toHaveClass('text-destructive')
    await user.click(trigger)
    const dialog = await screen.findByRole('dialog', { name: 'Cancelar visita' })
    const confirm = within(dialog).getByRole('button', { name: 'Confirmar cancelamento' })
    expect(confirm).toHaveClass('bg-destructive')
    expect(confirm.className).not.toMatch(/bg-primary/)
  })

  it('confirmação comum segue a cor do tema', async () => {
    mockFetch((_method, url) => {
      if (url === '/api/auth/me') return { body: me('ADMIN') }
      if (url.startsWith('/api/users?')) return { body: page([{ ...me('GATE'), active: true, employeeCode: null }]) }
    })
    const user = userEvent.setup()
    renderApp('/usuarios')

    await user.click(await screen.findByRole('button', { name: 'Redefinir senha' }))
    const dialog = await screen.findByRole('dialog')
    await waitFor(() => expect(within(dialog).getByRole('button', { name: 'Confirmar' })).toHaveClass('bg-primary'))
  })
})
