import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { fakeAuditEntry } from '@/test/auditFixtures'
import { page } from '@/test/leadFixtures'
import { me, mockFetch, renderApp } from '@/test/utils'
import { readableMetadata } from './labels'

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] })
  vi.setSystemTime(new Date('2026-09-24T15:00:00Z'))
})

afterEach(() => {
  vi.useRealTimers()
})

const users = page([
  { id: 'u-1', name: 'Administrador Fictício', email: 'admin@resort.local', role: 'ADMIN', active: true, mustChangePassword: false },
  { id: 'u-2', name: 'Porteiro Fictício', email: 'portaria@resort.local', role: 'GATE', active: true, mustChangePassword: false },
])

function renderAudit(pages: ReturnType<typeof page>[]) {
  const urls: string[] = []
  mockFetch((_method, url) => {
    if (url === '/api/auth/me') return { body: me('ADMIN') }
    if (url.startsWith('/api/users')) return { body: users }
    if (url.startsWith('/api/audit')) {
      urls.push(url)
      const pageNumber = Number(new URL(url, 'http://x').searchParams.get('page'))
      return { body: pages[Math.min(pageNumber, pages.length - 1)] }
    }
  })
  return { urls, ...renderApp('/auditoria') }
}

const rows = () =>
  within(screen.getByRole('table'))
    .getAllByRole('row')
    .slice(1)
    .map((row) => within(row).getAllByRole('cell').map((cell) => cell.textContent))

describe('XF3 — tabela de auditoria', () => {
  it('mostra data e hora no fuso da operação, usuário ou "Sistema", ação em português, entidade e IP, na ordem da API', async () => {
    renderAudit([
      page([
        fakeAuditEntry({ id: 'a-2', createdAt: '2026-09-24T03:15:00Z', userId: null, userName: 'Sistema', action: 'VISIT_NO_SHOW',
          entityType: 'VISIT', entityId: 'aaaabbbb-0000-0000-0000-000000000000', metadata: { leadId: 'l-1' }, ipAddress: null }),
        fakeAuditEntry({ id: 'a-1' }),
      ]),
    ])

    await screen.findByRole('table')
    expect(within(screen.getByRole('table')).getAllByRole('columnheader').map((c) => c.textContent)).toEqual([
      'Data e hora', 'Usuário', 'Ação', 'Entidade', 'Detalhes', 'IP',
    ])
    expect(rows()).toEqual([
      ['24/09/2026, 00:15', 'Sistema', 'Não compareceu', 'Visitaaaaabbbb', 'Lead: l-1', '—'],
      ['24/09/2026, 10:05', 'Administrador Fictício', 'Status do Lead alterado', 'Lead0f8b2c1e', 'De: NovoPara: Contatado', '10.0.0.7'],
    ])
  })
})

describe('XF4 — filtros e paginação', () => {
  it('os filtros viram parâmetros da API e trocar de página mantém os filtros', async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    const { urls } = renderAudit([
      { ...page([fakeAuditEntry({ id: 'p0' })]), totalPages: 2, totalElements: 21 },
      { ...page([fakeAuditEntry({ id: 'p1', userName: 'Na segunda página' })]), page: 1, totalPages: 2, totalElements: 21 },
    ])
    await screen.findByRole('table')
    expect(urls[0]).toBe('/api/audit?page=0&size=20&from=2026-08-26&to=2026-09-24')
    await screen.findByRole('option', { name: 'Porteiro Fictício' })

    fireEvent.change(screen.getByLabelText('De'), { target: { value: '2026-09-01' } })
    await user.selectOptions(screen.getByLabelText('Usuário'), 'u-2')
    await user.selectOptions(screen.getByLabelText('Ação'), 'Acesso negado')
    await user.selectOptions(screen.getByLabelText('Entidade'), 'Acesso')
    const filtered = '&from=2026-09-01&to=2026-09-24&userId=u-2&action=ACCESS_DENIED&entityType=ACCESS_RECORD'
    await waitFor(() => expect(urls.at(-1)).toBe(`/api/audit?page=0&size=20${filtered}`))

    await user.click(await screen.findByRole('button', { name: 'Próxima' }))
    await waitFor(() => expect(urls.at(-1)).toBe(`/api/audit?page=1&size=20${filtered}`))
    expect(await screen.findByText('Na segunda página')).toBeInTheDocument()
  })

  it('período inválido não é enviado', async () => {
    const { urls } = renderAudit([page([])])
    await screen.findByText('Nenhum registro no período.')

    fireEvent.change(screen.getByLabelText('De'), { target: { value: '2026-09-30' } })

    expect(await screen.findByRole('alert')).toHaveTextContent('Informe as duas datas, com a inicial até a final.')
    expect(urls).toHaveLength(1)
  })
})

describe('XF5 — metadata legível', () => {
  it('LEAD_STATUS_CHANGED com causa', () => {
    expect(readableMetadata({ from: 'VISIT_SCHEDULED', to: 'CONTACTED', cause: 'VISIT_CANCELLED' })).toEqual([
      'De: Visita agendada',
      'Para: Contatado',
      'Causa: Visita cancelada',
    ])
  })

  it('a ordem de leitura não depende da ordem das chaves no jsonb', () => {
    expect(readableMetadata({ to: 'VISITED', from: 'VISIT_SCHEDULED', cause: 'ACCESS_REGISTERED' })).toEqual([
      'De: Visita agendada',
      'Para: Visitou',
      'Causa: Entrada registrada',
    ])
    expect(readableMetadata({ rows: 2, type: 'ACCESS', filters: { to: null, from: null, status: 'DENIED' } })).toEqual([
      'Arquivo: Acessos',
      'Filtros · Status: Negado',
      'Linhas: 2',
    ])
  })

  it('EXPORT_GENERATED com filtros aninhados, sem os nulos', () => {
    expect(
      readableMetadata({
        type: 'VISITS',
        filters: { from: '2026-09-01', to: '2026-09-24', status: 'COMPLETED', prospectorId: null },
        rows: 42,
      }),
    ).toEqual([
      'Arquivo: Visitas',
      'Filtros · De: 01/09/2026',
      'Filtros · Até: 24/09/2026',
      'Filtros · Status: Realizada',
      'Linhas: 42',
    ])
  })

  it('metadata vazio aparece como "—"', async () => {
    renderAudit([page([fakeAuditEntry({ metadata: null, action: 'LOGIN' }), fakeAuditEntry({ id: 'a-2', metadata: {} })])])

    await screen.findByRole('table')
    expect(rows().map((row) => row[4])).toEqual(['—', '—'])
  })

  it('chave desconhecida aparece como veio, depois das conhecidas', () => {
    expect(readableMetadata({ novidade: 'x', active: false })).toEqual(['Ativo: Não', 'novidade: x'])
  })
})
