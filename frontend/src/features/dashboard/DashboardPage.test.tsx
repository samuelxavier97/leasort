import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { Role } from '@/features/auth/types'
import { fakeAdminSummary, fakeDenials, fakePerformance, fakeProspectorSummary, fakeVisitsByDay } from '@/test/dashboardFixtures'
import { page } from '@/test/leadFixtures'
import { me, mockFetch, problem, renderApp, type MockResponse } from '@/test/utils'

// Hoje = 24/09/2026 (quinta-feira) no fuso da operação.
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
  denials?: MockResponse
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
    if (url.startsWith('/api/dashboard/denials')) return overrides.denials ?? { body: fakeDenials() }
    if (url.startsWith('/api/dashboard/prospector-performance')) return { body: fakePerformance() }
  })
  return { urls, ...renderApp('/') }
}

/** [rótulo, valor] dos cartões de uma região. */
const cards = (region: string) =>
  within(screen.getByRole('region', { name: region }))
    .getAllByTestId('stat-value')
    .map((value) => [value.parentElement!.querySelector('p')!.textContent, value.textContent])

const comparisons = () =>
  within(screen.getByRole('region', { name: 'No período' }))
    .getAllByTestId('stat-comparison')
    .map((line) => [line.textContent, line.className])

const tableRows = (name: string) =>
  within(screen.getByRole('table', { name }))
    .getAllByRole('row')
    .map((row) => within(row).queryAllByRole(row.querySelector('th') ? 'columnheader' : 'cell').map((c) => c.textContent))

const queryOf = (url: string) => url.slice(url.indexOf('?'))
const pathOf = (url: string) => (url.includes('?') ? url.slice(0, url.indexOf('?')) : url)

describe('E1 — dashboard como tela inicial', () => {
  it.each(['ADMIN', 'PROSPECTOR'] as Role[])('%s cai em /dashboard', async (role) => {
    const { router } = renderDashboard(role)

    await waitFor(() => expect(router.state.location.pathname).toBe('/dashboard'))
    await waitFor(() => expect(screen.getByRole('heading', { level: 1, name: 'Dashboard' })).toBeInTheDocument())
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

describe('E2 — faixa e cartões do ADMIN (D-128)', () => {
  it('saudação, data por extenso, resumo de hoje e período na faixa', async () => {
    renderDashboard('ADMIN')

    const band = await screen.findByTestId('dashboard-band')
    await within(band).findByText('Boa tarde, Usuário')
    expect(band).toHaveTextContent('quinta-feira, 24 de setembro')
    expect(within(band).getByText('Hoje há 3 visitas; 1 já chegou.')).toBeInTheDocument()
    expect(within(band).getByText('Período: 26/08/2026 a 24/09/2026')).toBeInTheDocument()
    // Texto sobre a faixa na cor da D-118, sem transparência.
    expect(band).toHaveClass('bg-primary', 'text-primary-foreground')
    for (const text of band.querySelectorAll('p, h1, span')) {
      expect(text.className).not.toMatch(/opacity-|text-muted|foreground\//)
    }
  })

  it('cinco cartões do período com a comparação e cinco fotografias, sem perder nenhum indicador', async () => {
    renderDashboard('ADMIN')

    await screen.findByRole('region', { name: 'No período' })
    expect(cards('No período')).toEqual([
      ['Visitas realizadas', '40'],
      ['Comparecimento', '80%'],
      ['Sem comparecimento', '10'],
      ['Cancelamentos', '7'],
      ['Pessoas recebidas', '98'],
    ])
    expect(cards('Agora')).toEqual([
      ['Total de Leads', '120'],
      ['Leads atribuídos', '95'],
      ['Visitas agendadas', '14'],
      ['Visitas hoje', '3'],
      ['Convites ativos', '12'],
    ])
    // Contagens em %, taxa em p.p.; a cor diz se é bom ou ruim (menos faltas é bom), com seta e texto.
    expect(comparisons()).toEqual([
      ['▲ 25% a mais que nos 30 dias anteriores (melhora)', 'text-xs text-status-ok'],
      ['▲ 7 p.p. a mais que nos 30 dias anteriores (melhora)', 'text-xs text-status-ok'],
      ['▼ 17% a menos que nos 30 dias anteriores (melhora)', 'text-xs text-status-ok'],
      ['= igual aos 30 dias anteriores', 'text-xs text-muted-foreground'],
      ['sem comparação', 'text-xs text-muted-foreground'],
    ])
  })

  it('mais faltas é ruim, mesmo subindo', async () => {
    renderDashboard('ADMIN', { summary: { body: fakeAdminSummary({ noShows: 18 }) } })

    await screen.findByRole('region', { name: 'No período' })
    expect(comparisons()[2]).toEqual(['▲ 50% a mais que nos 30 dias anteriores (piora)', 'text-xs text-status-denied'])
  })
})

describe('E3 — Hoje na sala de vendas (D-128)', () => {
  it('na ordem da API, com Prospector, acompanhantes e a hora da entrada', async () => {
    renderDashboard('ADMIN')

    const panel = await screen.findByRole('region', { name: 'Hoje na sala de vendas' })
    expect(within(panel).getAllByRole('listitem').map((item) => item.textContent)).toEqual([
      'Lead ChegouAna Prospectora · 2 de 3 acompanhantes presentesChegou às 09:40',
      'Lead AgendadoBia Prospectora · 1 acompanhanteAgendada',
      'Lead SozinhoBia Prospectora · sem acompanhantesAgendada',
    ])
    expect(within(panel).getByRole('link', { name: 'Ver chegadas' })).toHaveAttribute('href', '/chegadas')
  })

  it('sem visitas, avisa', async () => {
    renderDashboard('ADMIN', { summary: { body: fakeAdminSummary({ visitsToday: 0, arrivedToday: 0, todayVisits: [] }) } })

    const panel = await screen.findByRole('region', { name: 'Hoje na sala de vendas' })
    expect(within(panel).getByText('Nenhuma visita para hoje.')).toBeInTheDocument()
    expect(within(screen.getByTestId('dashboard-band')).getByText('Nenhuma visita para hoje.')).toBeInTheDocument()
  })
})

describe('E4 — período e filtros do ADMIN', () => {
  it('período padrão de 30 dias nos quatro pedidos (01:30 UTC ainda é o dia anterior)', async () => {
    vi.setSystemTime(new Date('2026-09-25T01:30:00Z'))
    const { urls } = renderDashboard('ADMIN')

    await waitFor(() => expect(urls).toHaveLength(4))
    const query = '?from=2026-08-26&to=2026-09-24'
    expect([...urls].sort()).toEqual([
      `/api/dashboard/denials${query}`,
      `/api/dashboard/prospector-performance${query}`,
      `/api/dashboard/summary${query}`,
      `/api/dashboard/visits-by-day${query}`,
    ])
    expect(within(screen.getByRole('group', { name: 'Período' })).getByRole('button', { name: '30 dias' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.queryByLabelText('De')).not.toBeInTheDocument()
  })

  it('7, 30 e 90 dias em botões; "Personalizado" mantém as duas datas', async () => {
    const user = userEvent.setup()
    const { urls } = renderDashboard('ADMIN')
    await waitFor(() => expect(urls).toHaveLength(4))
    const group = screen.getByRole('group', { name: 'Período' })
    expect(within(group).getAllByRole('button').map((button) => button.textContent)).toEqual([
      '7 dias',
      '30 dias',
      '90 dias',
      'Personalizado',
    ])

    await user.click(within(group).getByRole('button', { name: '7 dias' }))
    await waitFor(() => expect(urls).toHaveLength(8))
    expect(urls.slice(4).map(queryOf)).toEqual(Array(4).fill('?from=2026-09-18&to=2026-09-24'))

    await user.click(within(group).getByRole('button', { name: '90 dias' }))
    await waitFor(() => expect(urls).toHaveLength(12))
    expect(urls.slice(8).map(queryOf)).toEqual(Array(4).fill('?from=2026-06-27&to=2026-09-24'))

    await user.click(within(group).getByRole('button', { name: 'Personalizado' }))
    expect(screen.getByLabelText('De')).toHaveValue('2026-06-27')
    fireEvent.change(screen.getByLabelText('De'), { target: { value: '2026-09-01' } })
    await waitFor(() => expect(urls).toHaveLength(16))
    expect(urls.slice(12).map(queryOf)).toEqual(Array(4).fill('?from=2026-09-01&to=2026-09-24'))
  })

  it('Prospector filtra resumo, série e negativas; o desempenho continua comparando todos', async () => {
    const { urls } = renderDashboard('ADMIN')
    await waitFor(() => expect(urls).toHaveLength(4))
    await screen.findByRole('option', { name: 'Bia Prospectora (P-02)' })

    fireEvent.change(screen.getByLabelText('Prospector'), { target: { value: 'p-2' } })
    await waitFor(() => expect(urls).toHaveLength(7))
    const filtered = '?from=2026-08-26&to=2026-09-24&prospectorId=p-2'
    expect(urls.slice(4).map(pathOf).sort()).toEqual([
      '/api/dashboard/denials',
      '/api/dashboard/summary',
      '/api/dashboard/visits-by-day',
    ])
    expect(urls.slice(4).map(queryOf)).toEqual([filtered, filtered, filtered])
    expect(await screen.findByText(/Com o filtro de Prospector, "Código inválido" não entra/)).toBeInTheDocument()
  })

  it('from > to é bloqueado com mensagem e não é enviado', async () => {
    const user = userEvent.setup()
    const { urls } = renderDashboard('ADMIN')
    await waitFor(() => expect(urls).toHaveLength(4))
    await user.click(screen.getByRole('button', { name: 'Personalizado' }))

    fireEvent.change(screen.getByLabelText('De'), { target: { value: '2026-09-30' } })

    expect(await screen.findByRole('alert')).toHaveTextContent('A data inicial não pode ser posterior à final.')
    await new Promise((resolve) => setTimeout(resolve, 50))
    expect(urls).toHaveLength(4)
  })
})

describe('E5 — desempenho por Prospector (D-128)', () => {
  it('na ordem da API, com o 1º lugar no detalhe da identidade; clicar filtra e clicar de novo tira', async () => {
    const user = userEvent.setup()
    const { urls } = renderDashboard('ADMIN')
    await screen.findByRole('region', { name: 'Desempenho por Prospector' })
    await screen.findByRole('option', { name: 'Bia Prospectora (P-02)' })
    const panel = screen.getByRole('region', { name: 'Desempenho por Prospector' })
    expect(within(panel).getAllByRole('row').map((row) => row.textContent)).toEqual([
      'PosiçãoProspectorRealizadasSem comparecimentoComparecimento',
      '1Bia Prospectora301075%',
      '2Ana Prospectora100100%',
    ])
    expect(within(panel).getByTestId('first-place')).toHaveClass('bg-brand-accent', 'text-brand-accent-foreground')
    const before = urls.length

    await user.click(within(panel).getByRole('button', { name: 'Filtrar por Bia Prospectora' }))
    await waitFor(() => expect(urls.length).toBe(before + 3))
    expect(urls.slice(before).every((url) => url.endsWith('prospectorId=p-2'))).toBe(true)
    expect(screen.getByLabelText('Prospector')).toHaveValue('p-2')
    expect(within(panel).getByRole('button', { name: 'Tirar o filtro de Bia Prospectora' })).toHaveAttribute('aria-pressed', 'true')

    await user.click(within(panel).getByRole('button', { name: 'Tirar o filtro de Bia Prospectora' }))
    await waitFor(() => expect(screen.getByLabelText('Prospector')).toHaveValue(''))
  })
})

describe('E6 — por que a portaria negou (D-129)', () => {
  it('as negativas por motivo com os números, a tabela, e sem "Acessos por dia"', async () => {
    renderDashboard('ADMIN')

    const panel = await screen.findByRole('region', { name: 'Por que a portaria negou' })
    expect(panel).toHaveTextContent('30 tentativas negadas')
    expect(tableRows('Por que a portaria negou — tabela')).toEqual([
      ['Motivo', 'Tentativas'],
      ['Código inválido', '16'],
      ['Convite já utilizado', '7'],
      ['Fora da data', '4'],
      ['Convite cancelado', '2'],
      ['Convite expirado', '1'],
    ])
    expect(panel.querySelectorAll('.bg-status-denied')).toHaveLength(5)
    expect(screen.queryByRole('region', { name: 'Acessos por dia' })).not.toBeInTheDocument()
  })

  it('sem negativas, avisa', async () => {
    renderDashboard('ADMIN', { denials: { body: fakeDenials({ total: 0, reasons: [] }) } })

    expect(await screen.findByText('Nenhuma negativa no período.')).toBeInTheDocument()
  })

  it('a série de visitas usa "Sem comparecimento"', async () => {
    renderDashboard('ADMIN')

    await screen.findByRole('table', { name: 'Visitas por dia — tabela' })
    expect(tableRows('Visitas por dia — tabela')).toEqual([
      ['Data', 'Agendadas', 'Realizadas', 'Sem comparecimento', 'Cancelamentos'],
      ['22/09/2026', '0', '2', '1', '0'],
      ['23/09/2026', '0', '0', '0', '0'],
      ['24/09/2026', '3', '1', '0', '1'],
    ])
    expect(document.body.textContent).not.toContain('No-show')
  })
})

describe('E7 — PROSPECTOR', () => {
  it('saudação, cartões com comparação, hoje e próximas visitas', async () => {
    renderDashboard('PROSPECTOR')

    await screen.findByRole('region', { name: 'Indicadores' })
    expect(within(screen.getByTestId('dashboard-band')).getByText('Bom dia, Usuário')).toBeInTheDocument()
    expect(cards('Indicadores')).toEqual([
      ['Visitas realizadas', '9'],
      ['Comparecimento', '90%'],
      ['Meus Leads', '22'],
      ['Visitas agendadas', '4'],
      ['Visitas hoje', '1'],
      ['Convites ativos', '4'],
    ])
    const lines = within(screen.getByRole('region', { name: 'Indicadores' })).getAllByTestId('stat-comparison')
    expect(lines.map((line) => line.textContent)).toEqual([
      '▼ 10% a menos que nos 30 dias anteriores (piora)',
      '▼ 10 p.p. a menos que nos 30 dias anteriores (piora)',
    ])
    expect(within(screen.getByRole('region', { name: 'Hoje na sala de vendas' })).getByText('Lead de Hoje')).toBeInTheDocument()
    const upcoming = within(screen.getByRole('region', { name: 'Próximas visitas' })).getAllByRole('listitem')
    expect(upcoming.map((item) => item.textContent)).toEqual([
      'Lead Amanhã25/09/2026 · 1 acompanhante',
      'Lead Depois30/09/2026 · 3 acompanhantes',
    ])
    expect(within(upcoming[0]).getByRole('link')).toHaveAttribute('href', '/visitas/v-2')
  })

  it('só 7, 30 e 90 dias, sem "Personalizado" nem Prospector; nunca pede negativas, desempenho ou prospectorId', async () => {
    const user = userEvent.setup()
    const { urls } = renderDashboard('PROSPECTOR')
    await screen.findByRole('table', { name: 'Minhas visitas por dia — tabela' })
    const group = screen.getByRole('group', { name: 'Período' })
    expect(within(group).getAllByRole('button').map((button) => button.textContent)).toEqual(['7 dias', '30 dias', '90 dias'])
    expect(screen.queryByLabelText('De')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('Prospector')).not.toBeInTheDocument()
    expect([...urls].map(pathOf).sort()).toEqual(['/api/dashboard/summary', '/api/dashboard/visits-by-day'])
    expect(urls.map(queryOf)).toEqual(Array(2).fill('?from=2026-08-26&to=2026-09-24'))

    await user.click(within(group).getByRole('button', { name: '7 dias' }))
    await waitFor(() => expect(urls).toHaveLength(4))
    expect(urls.slice(2).map(queryOf)).toEqual(Array(2).fill('?from=2026-09-18&to=2026-09-24'))
    expect(urls.some((url) => url.includes('prospectorId') || url.includes('denials') || url.includes('performance'))).toBe(false)
  })

  it('sem próximas visitas, avisa', async () => {
    renderDashboard('PROSPECTOR', { summary: { body: fakeProspectorSummary({ upcomingVisits: [] }) } })

    expect(await screen.findByText('Nenhuma visita agendada a partir de hoje.')).toBeInTheDocument()
  })
})

describe('E8 — erros em português', () => {
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
