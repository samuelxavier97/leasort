import { act, fireEvent, screen, waitFor, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { Role } from '@/features/auth/types'
import { fakeArrival } from '@/test/arrivalFixtures'
import { me, mockFetch, problem, renderApp, type MockResponse } from '@/test/utils'
import type { Arrival } from './api'

// Hoje = 24/09/2026 no fuso da operação (12:00 em São Paulo).
beforeEach(() => {
  vi.useFakeTimers({ shouldAdvanceTime: true })
  vi.setSystemTime(new Date('2026-09-24T15:00:00Z'))
})

afterEach(() => {
  vi.useRealTimers()
})

const list = (arrivals: Arrival[], date = '2026-09-24'): MockResponse => ({ body: { date, arrivals } })

/** Respostas de /api/arrivals em sequência; a última se repete. Devolve as URLs pedidas. */
function renderArrivals(role: Role, responses: MockResponse[], meResponse?: () => MockResponse) {
  const urls: string[] = []
  mockFetch((_method, url) => {
    if (url === '/api/auth/me') return meResponse?.() ?? { body: me(role) }
    if (url.startsWith('/api/arrivals')) {
      urls.push(url)
      return responses[Math.min(urls.length - 1, responses.length - 1)]
    }
  })
  return { urls, ...renderApp('/chegadas') }
}

const rows = () =>
  within(screen.getByRole('table'))
    .getAllByRole('row')
    .slice(1)
    .map((row) => within(row).getAllByRole('cell').map((cell) => cell.textContent))

describe('F2 — lista de chegadas', () => {
  it('mostra hora no fuso da operação, Lead, presentes e Prospector na ordem da API, com link para a ficha', async () => {
    renderArrivals('HOST', [
      list([
        fakeArrival({ visitId: 'v-2', entryAt: '2026-09-24T14:40:00Z', leadName: 'Lead Dois', companionsPresent: 0 }),
        fakeArrival({ visitId: 'v-1', entryAt: '2026-09-24T12:05:00Z', leadName: 'Lead Um', prospectorName: 'Outro Prospector' }),
      ]),
    ])

    await screen.findByRole('table')
    // Nome acessível de cada coluna (o rótulo curto do celular é aria-hidden).
    const headerNames = within(screen.getByRole('table'))
      .getAllByRole('columnheader')
      .map((cell) => Array.from(cell.querySelectorAll(':scope > :not([aria-hidden])')).map((n) => n.textContent).join('') || cell.textContent)
    expect(headerNames).toEqual([
      'Hora',
      'Lead',
      'Acompanhantes presentes',
      'Prospector',
      'Ficha',
    ])
    expect(rows()).toEqual([
      ['11:40', 'Lead Dois', '0', 'Prospector Fictício', 'Ficha'],
      ['09:05', 'Lead Um', '2', 'Outro Prospector', 'Ficha'],
    ])
    expect(screen.getByRole('link', { name: 'Ficha de Lead Um' })).toHaveAttribute('href', '/chegadas/v-1/ficha')
  })

  it('sem chegadas, avisa', async () => {
    renderArrivals('HOST', [list([])])

    expect(await screen.findByText('Nenhuma chegada registrada neste dia.')).toBeInTheDocument()
  })
})

describe('F3 — HOST e PROSPECTOR veem hoje, pela data do backend', () => {
  it.each(['HOST', 'PROSPECTOR'] as Role[])('%s pede sem date, não vê seletor e usa o date da resposta', async (role) => {
    const { urls } = renderArrivals(role, [list([], '2026-09-25')])

    expect(await screen.findByRole('heading', { name: 'Chegadas de hoje' })).toBeInTheDocument()
    expect(await screen.findByText('Entradas de 25/09/2026, da mais recente para a mais antiga.')).toBeInTheDocument()
    expect(urls).toEqual(['/api/arrivals'])
    expect(screen.queryByLabelText('Data')).not.toBeInTheDocument()
  })
})

describe('F4 — seletor de data do ADMIN', () => {
  it('começa em hoje em São Paulo e trocar a data pede ?date=', async () => {
    // 01:30 UTC de 25/09 ainda é 24/09 em São Paulo.
    vi.setSystemTime(new Date('2026-09-25T01:30:00Z'))
    const { urls } = renderArrivals('ADMIN', [list([])])

    expect(await screen.findByRole('heading', { name: 'Chegadas' })).toBeInTheDocument()
    const input = screen.getByLabelText('Data')
    expect(input).toHaveValue('2026-09-24')
    await waitFor(() => expect(urls).toEqual(['/api/arrivals?date=2026-09-24']))

    fireEvent.change(input, { target: { value: '2026-09-20' } })

    await waitFor(() => expect(urls.at(-1)).toBe('/api/arrivals?date=2026-09-20'))
  })
})

describe('F5 — polling de 30 s', () => {
  it('pede de novo aos 30 s, não aos 29 s, e a nova chegada aparece', async () => {
    const { urls } = renderArrivals('HOST', [
      list([fakeArrival({ leadName: 'Lead Um' })]),
      list([fakeArrival({ visitId: 'v-2', leadName: 'Lead Novo' }), fakeArrival({ leadName: 'Lead Um' })]),
    ])
    await screen.findByText('Lead Um')

    await act(() => vi.advanceTimersByTimeAsync(29_000))
    expect(urls).toHaveLength(1)
    await act(() => vi.advanceTimersByTimeAsync(1_000))

    expect(await screen.findByText('Lead Novo')).toBeInTheDocument()
    expect(urls).toHaveLength(2)
  })

  it('ADMIN numa data passada não faz polling; de volta a hoje, faz', async () => {
    const { urls } = renderArrivals('ADMIN', [list([])])
    await waitFor(() => expect(urls).toHaveLength(1))
    const input = screen.getByLabelText('Data')
    fireEvent.change(input, { target: { value: '2026-09-20' } })
    await waitFor(() => expect(urls.at(-1)).toBe('/api/arrivals?date=2026-09-20'))
    const count = urls.length

    await act(() => vi.advanceTimersByTimeAsync(90_000))
    expect(urls.length).toBe(count)

    // De volta a hoje: a troca de data já pede uma vez; o polling pede de novo 30 s depois.
    fireEvent.change(input, { target: { value: '2026-09-24' } })
    await waitFor(() => expect(urls).toHaveLength(count + 1))
    await act(() => vi.advanceTimersByTimeAsync(30_000))
    expect(urls.slice(count)).toEqual(['/api/arrivals?date=2026-09-24', '/api/arrivals?date=2026-09-24'])
  })

  it('sair da tela para o polling', async () => {
    const { urls, router } = renderArrivals('PROSPECTOR', [list([])])
    await waitFor(() => expect(urls).toHaveLength(1))

    await act(() => router.navigate('/agenda'))
    await act(() => vi.advanceTimersByTimeAsync(90_000))

    expect(urls.filter((url) => url.startsWith('/api/arrivals'))).toHaveLength(1)
  })

  it('401 durante o polling leva ao login', async () => {
    const { router } = renderArrivals('HOST', [
      list([fakeArrival()]),
      problem(401, 'UNAUTHENTICATED'),
    ])
    await screen.findByText('Lead Fictício')

    await act(() => vi.advanceTimersByTimeAsync(30_000))

    await waitFor(() => expect(router.state.location.pathname).toBe('/login'))
  })

  it('403 PASSWORD_CHANGE_REQUIRED durante o polling leva à troca de senha', async () => {
    let mustChange = false
    const { router } = renderArrivals(
      'HOST',
      [list([fakeArrival()]), problem(403, 'PASSWORD_CHANGE_REQUIRED')],
      () => ({ body: me('HOST', { mustChangePassword: mustChange }) }),
    )
    await screen.findByText('Lead Fictício')
    mustChange = true

    await act(() => vi.advanceTimersByTimeAsync(30_000))

    await waitFor(() => expect(router.state.location.pathname).toBe('/trocar-senha'))
  })

  it.each([
    { label: 'falha de rede', failure: 'network' as const },
    { label: '5xx', failure: problem(503, 'ERROR') },
  ])('$label mantém a lista com o aviso, e o sucesso seguinte remove o aviso', async ({ failure }) => {
    let call = 0
    mockFetch((_method, url) => {
      if (url === '/api/auth/me') return { body: me('HOST') }
      if (url.startsWith('/api/arrivals')) {
        call += 1
        if (call === 2) {
          if (failure === 'network') throw new TypeError('Failed to fetch')
          return failure
        }
        return list([fakeArrival({ leadName: call === 1 ? 'Lead Um' : 'Lead Depois' })])
      }
    })
    renderApp('/chegadas')
    await screen.findByText('Lead Um')

    await act(() => vi.advanceTimersByTimeAsync(30_000))
    expect(await screen.findByRole('status')).toHaveTextContent('Não foi possível atualizar. Nova tentativa em instantes.')
    expect(screen.getByText('Lead Um')).toBeInTheDocument()

    await act(() => vi.advanceTimersByTimeAsync(30_000))
    expect(await screen.findByText('Lead Depois')).toBeInTheDocument()
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })
})
