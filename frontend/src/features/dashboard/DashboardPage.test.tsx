import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { Role } from '@/features/auth/types'
import { fakeAccessByDay, fakeAdminSummary, fakeProspectorSummary, fakeVisitsByDay } from '@/test/dashboardFixtures'
import { page } from '@/test/leadFixtures'
import { me, mockFetch, problem, renderApp, type MockResponse } from '@/test/utils'

// Hoje = 24/09/2026 no fuso da operação.
beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] })
  vi.setSystemTime(new Date('2026-09-24T15:00:00Z'))
})

afterEach(() => {
  vi.useRealTimers()
})

const prospectors = page([
  { id: 'p-1', userId: 'u-1', name: 'Ana Prospectora', email: 'ana@resort.local', active: true, employeeCode: 'P-01', phone: null },
  { id: 'p-2', userId: 'u-2', name: 'Bia Prospectora', email: 'bia@resort.local', active: true, employeeCode: 'P-02', phone: null },
])

interface Overrides {
  summary?: MockResponse
  visits?: MockResponse
  access?: MockResponse
}

/** Devolve as URLs pedidas ao dashboard, na ordem. */
function renderDashboard(role: Role, overrides: Overrides = {}) {
  const urls: string[] = []
  mockFetch((_method, url) => {
    if (url === '/api/auth/me') return { body: me(role) }
    if (url.startsWith('/api/prospectors')) return { body: prospectors }
    if (url.startsWith('/api/dashboard/')) urls.push(url)
    if (url.startsWith('/api/dashboard/summary')) {
      return overrides.summary ?? { body: role === 'ADMIN' ? fakeAdminSummary() : fakeProspectorSummary() }
    }
    if (url.startsWith('/api/dashboard/visits-by-day')) return overrides.visits ?? { body: fakeVisitsByDay() }
    if (url.startsWith('/api/dashboard/access-by-day')) return overrides.access ?? { body: fakeAccessByDay() }
  })
  return { urls, ...renderApp('/') }
}

const cards = () =>
  within(screen.getByRole('region', { name: 'Indicadores' }))
    .getAllByTestId('stat-value')
    .map((value) => [value.previousElementSibling?.textContent, value.textContent])

const tableRows = (name: string) =>
  within(screen.getByRole('table', { name }))
    .getAllByRole('row')
    .map((row) => within(row).queryAllByRole(row.querySelector('th') ? 'columnheader' : 'cell').map((c) => c.textContent))

describe('E1 — dashboard como tela inicial', () => {
  it.each(['ADMIN', 'PROSPECTOR'] as Role[])('%s cai em /dashboard', async (role) => {
    const { router } = renderDashboard(role)

    await waitFor(() => expect(router.state.location.pathname).toBe('/dashboard'))
    expect(await screen.findByRole('heading', { name: 'Dashboard' })).toBeInTheDocument()
  })

  it.each([
    { role: 'GATE' as Role, landing: '/portaria' },
    { role: 'HOST' as Role, landing: '/chegadas' },
  ])('$role não acessa /dashboard', async ({ role, landing }) => {
    const { router, urls } = renderDashboard(role)
    await waitFor(() => expect(router.state.location.pathname).toBe(landing))

    await router.navigate('/dashboard')

    await waitFor(() => expect(router.state.location.pathname).toBe(landing))
    expect(urls).toEqual([])
  })
})

describe('E2 — cartões do ADMIN', () => {
  it('mostra os 9 cartões com rótulos e valores, e o período em DD/MM/AAAA', async () => {
    renderDashboard('ADMIN')

    expect(await screen.findByText('Período: 26/08/2026 a 24/09/2026 · Hoje: 24/09/2026')).toBeInTheDocument()
    expect(cards()).toEqual([
      ['Total de Leads', '120'],
      ['Leads atribuídos', '95'],
      ['Visitas agendadas', '14'],
      ['Visitas hoje', '3'],
      ['Convites ativos', '12'],
      ['Visitas realizadas', '40'],
      ['No-show', '5'],
      ['Cancelamentos', '7'],
      ['Pessoas recebidas', '98'],
    ])
  })
})

describe('E3 — PROSPECTOR', () => {
  it('mostra 5 cartões e as próximas visitas na ordem da API, com link, sem filtros', async () => {
    renderDashboard('PROSPECTOR')

    await screen.findByRole('region', { name: 'Indicadores' })
    expect(cards()).toEqual([
      ['Meus Leads', '22'],
      ['Visitas agendadas', '4'],
      ['Visitas hoje', '1'],
      ['Convites ativos', '4'],
      ['Visitas realizadas', '9'],
    ])
    const upcoming = within(screen.getByRole('region', { name: 'Próximas visitas' })).getAllByRole('listitem')
    expect(upcoming.map((item) => item.textContent)).toEqual([
      'Lead Amanhã25/09/2026 · 1 acompanhante',
      'Lead Depois30/09/2026 · 3 acompanhantes',
    ])
    expect(within(upcoming[0]).getByRole('link')).toHaveAttribute('href', '/visitas/v-2')
    expect(screen.queryByRole('group', { name: 'Filtros' })).not.toBeInTheDocument()
    expect(screen.queryByLabelText('De')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('Prospector')).not.toBeInTheDocument()
  })

  it('sem próximas visitas, avisa', async () => {
    renderDashboard('PROSPECTOR', { summary: { body: fakeProspectorSummary({ upcomingVisits: [] }) } })

    expect(await screen.findByText('Nenhuma visita agendada a partir de hoje.')).toBeInTheDocument()
  })
})

describe('E4 — filtros do ADMIN', () => {
  it('período padrão pela D-080 nos três pedidos (01:30 UTC ainda é o dia anterior)', async () => {
    vi.setSystemTime(new Date('2026-09-25T01:30:00Z'))
    const { urls } = renderDashboard('ADMIN')

    await waitFor(() => expect(urls).toHaveLength(3))
    const query = '?from=2026-08-26&to=2026-09-24'
    expect([...urls].sort()).toEqual([
      `/api/dashboard/access-by-day${query}`,
      `/api/dashboard/summary${query}`,
      `/api/dashboard/visits-by-day${query}`,
    ])
    expect(screen.getByLabelText('De')).toHaveValue('2026-08-26')
    expect(screen.getByLabelText('Até')).toHaveValue('2026-09-24')
  })

  it('trocar período ou Prospector refaz os três pedidos com os mesmos parâmetros', async () => {
    const { urls } = renderDashboard('ADMIN')
    await waitFor(() => expect(urls).toHaveLength(3))
    await screen.findByRole('option', { name: 'Bia Prospectora (P-02)' })

    fireEvent.change(screen.getByLabelText('De'), { target: { value: '2026-09-01' } })
    await waitFor(() => expect(urls).toHaveLength(6))
    const period = '?from=2026-09-01&to=2026-09-24'
    expect(urls.slice(3).map((url) => url.slice(url.indexOf('?')))).toEqual([period, period, period])

    fireEvent.change(screen.getByLabelText('Prospector'), { target: { value: 'p-2' } })
    await waitFor(() => expect(urls).toHaveLength(9))
    const filtered = '?from=2026-09-01&to=2026-09-24&prospectorId=p-2'
    expect(urls.slice(6).map((url) => url.slice(url.indexOf('?')))).toEqual([filtered, filtered, filtered])
    expect(urls.slice(6).map((url) => url.slice(0, url.indexOf('?'))).sort()).toEqual([
      '/api/dashboard/access-by-day',
      '/api/dashboard/summary',
      '/api/dashboard/visits-by-day',
    ])
  })

  it('from > to é bloqueado com mensagem e não é enviado', async () => {
    const { urls } = renderDashboard('ADMIN')
    await waitFor(() => expect(urls).toHaveLength(3))

    fireEvent.change(screen.getByLabelText('De'), { target: { value: '2026-09-30' } })

    expect(await screen.findByRole('alert')).toHaveTextContent('A data inicial não pode ser posterior à final.')
    await new Promise((resolve) => setTimeout(resolve, 50))
    expect(urls).toHaveLength(3)
  })
})

describe('E5 — tabelas equivalentes dos gráficos', () => {
  it('trazem todos os dias, inclusive os de zero, em DD/MM/AAAA', async () => {
    renderDashboard('ADMIN')

    await screen.findByRole('table', { name: 'Visitas por dia — tabela' })
    expect(tableRows('Visitas por dia — tabela')).toEqual([
      ['Data', 'Agendadas', 'Realizadas', 'No-show', 'Cancelamentos'],
      ['22/09/2026', '0', '2', '1', '0'],
      ['23/09/2026', '0', '0', '0', '0'],
      ['24/09/2026', '3', '1', '0', '1'],
    ])
    expect(tableRows('Acessos por dia — tabela')).toEqual([
      ['Data', 'Liberados', 'Negados'],
      ['22/09/2026', '2', '1'],
      ['23/09/2026', '0', '0'],
      ['24/09/2026', '1', '4'],
    ])
  })

  it('PROSPECTOR tem a tabela das próprias visitas por dia', async () => {
    renderDashboard('PROSPECTOR')

    expect(await screen.findByRole('table', { name: 'Minhas visitas por dia — tabela' })).toBeInTheDocument()
    expect(screen.queryByRole('table', { name: 'Acessos por dia — tabela' })).not.toBeInTheDocument()
  })
})

describe('E6 — PROSPECTOR não escolhe escopo', () => {
  it('nunca pede access-by-day e nunca manda prospectorId, from ou to', async () => {
    const { urls } = renderDashboard('PROSPECTOR')

    await screen.findByRole('table', { name: 'Minhas visitas por dia — tabela' })
    expect([...urls].sort()).toEqual(['/api/dashboard/summary', '/api/dashboard/visits-by-day'])
  })
})

describe('E7 — erros em português', () => {
  it('PERIOD_TOO_LONG', async () => {
    renderDashboard('ADMIN', { summary: problem(400, 'PERIOD_TOO_LONG', 'too long') })

    expect(await screen.findByRole('alert')).toHaveTextContent('O período pode ter no máximo 366 dias.')
  })

  it('PROSPECTOR_NOT_FOUND no filtro', async () => {
    renderDashboard('ADMIN', { summary: problem(404, 'PROSPECTOR_NOT_FOUND') })

    expect(await screen.findByRole('alert')).toHaveTextContent('Prospector não encontrado.')
  })

  it('falha de servidor no PROSPECTOR', async () => {
    renderDashboard('PROSPECTOR', { summary: problem(500, 'ERROR') })

    expect(await screen.findByText('Não foi possível concluir a operação.')).toBeInTheDocument()
  })
})
