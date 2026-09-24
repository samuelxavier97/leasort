import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { Role } from '@/features/auth/types'
import type { Visit } from './api'
import { page } from '@/test/leadFixtures'
import { me, mockFetch, renderApp } from '@/test/utils'
import { fakeVisit } from '@/test/visitFixtures'

// 01h30 de 25/09/2026 em UTC ainda é 24/09/2026 em São Paulo: a Agenda começa em 24/09 (D-080).
beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] })
  vi.setSystemTime(new Date('2026-09-25T01:30:00Z'))
})

afterEach(() => {
  vi.useRealTimers()
})

function mockList(role: Role, visits: Visit[]) {
  const requested: string[] = []
  mockFetch((_method, url) => {
    if (url === '/api/auth/me') return { body: me(role) }
    if (url.startsWith('/api/visits?')) {
      requested.push(url)
      return { body: page(visits) }
    }
    if (url.startsWith('/api/prospectors')) return { body: page([]) }
  })
  return requested
}

function rowTexts() {
  return within(screen.getByRole('table'))
    .getAllByRole('row')
    .slice(1)
    .map((row) => row.textContent)
}

describe('W9 — Agenda e Histórico', () => {
  it('Agenda pede as visitas SCHEDULED de hoje (no fuso da operação) em diante, em ordem de data', async () => {
    const requested = mockList('PROSPECTOR', [
      fakeVisit({ id: 'v-1', scheduledDate: '2026-09-24', lead: { id: 'l-1', name: 'Lead Hoje', accessible: true } }),
      fakeVisit({ id: 'v-2', scheduledDate: '2026-10-02', lead: { id: 'l-2', name: 'Lead Outubro', accessible: true }, companions: [] }),
    ])
    renderApp('/agenda')

    expect(await screen.findByRole('heading', { name: 'Agenda' })).toBeInTheDocument()
    await waitFor(() => expect(rowTexts()).toEqual(['24/09/2026Lead HojeAgendada0', '02/10/2026Lead OutubroAgendada0']))
    expect(requested).toEqual(['/api/visits?page=0&status=SCHEDULED&from=2026-09-24&order=asc'])
    expect(screen.getByRole('link', { name: '24/09/2026' })).toHaveAttribute('href', '/visitas/v-1')
  })

  it('Histórico pede as demais visitas (scope=history, D-079), da mais recente para a mais antiga', async () => {
    const requested = mockList('PROSPECTOR', [
      fakeVisit({ id: 'v-3', scheduledDate: '2026-10-20', status: 'CANCELLED', canEdit: false }),
      fakeVisit({ id: 'v-4', scheduledDate: '2026-09-20', status: 'SCHEDULED', canEdit: true }),
      fakeVisit({ id: 'v-5', scheduledDate: '2026-08-01', status: 'NO_SHOW', canEdit: false }),
    ])
    renderApp('/historico')

    expect(await screen.findByRole('heading', { name: 'Histórico' })).toBeInTheDocument()
    await waitFor(() =>
      expect(rowTexts()).toEqual([
        '20/10/2026Lead FictícioCancelada0Prospector Fictício',
        '20/09/2026Lead FictícioAgendada0Prospector Fictício',
        '01/08/2026Lead FictícioNão compareceu0Prospector Fictício',
      ]),
    )
    expect(requested).toEqual(['/api/visits?page=0&order=desc&scope=history'])
  })

  it('listas vazias mostram a mensagem de cada tela', async () => {
    mockList('PROSPECTOR', [])
    renderApp('/agenda')

    expect(await screen.findByText('Nenhuma visita agendada de hoje em diante.')).toBeInTheDocument()
  })

  it('Visitas do ADMIN lista tudo e filtra por status', async () => {
    const requested = mockList('ADMIN', [fakeVisit()])
    const user = userEvent.setup()
    renderApp('/visitas')

    expect(await screen.findByRole('heading', { name: 'Visitas' })).toBeInTheDocument()
    await waitFor(() => expect(requested).toContain('/api/visits?page=0&order=asc'))
    await user.selectOptions(screen.getByLabelText('Status'), 'Cancelada')

    await waitFor(() => expect(requested).toContain('/api/visits?page=0&status=CANCELLED&order=asc'))
  })
})
