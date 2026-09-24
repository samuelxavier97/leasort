import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import type { Role } from '@/features/auth/types'
import type { LeadStatus } from './api'
import { fakeLead, page } from '@/test/leadFixtures'
import { fakeVisit } from '@/test/visitFixtures'
import { me, mockFetch, problem, renderApp } from '@/test/utils'

describe('LeadDetailPage', () => {
  it('mostra as ações conforme status e perfil', async () => {
    mockFetch((_method, url) => {
      if (url === '/api/auth/me') return { body: me('ADMIN') }
      if (url === '/api/leads/lead-1') return { body: fakeLead({ status: 'CANCELLED' }) }
      if (url.startsWith('/api/visits?')) return { body: page([]) }
    })
    renderApp('/leads/lead-1')

    expect(await screen.findByRole('button', { name: 'Reativar' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Registrar contato' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Descartar Lead' })).not.toBeInTheDocument()
  })

  it('transição recusada pelo backend mostra a mensagem em português', async () => {
    mockFetch((method, url) => {
      if (url === '/api/auth/me') return { body: me('PROSPECTOR') }
      if (method === 'GET' && url === '/api/leads/lead-1') return { body: fakeLead({ status: 'NEW' }) }
      if (method === 'PATCH' && url === '/api/leads/lead-1/status') return problem(409, 'INVALID_STATUS_TRANSITION')
      if (url.startsWith('/api/visits?')) return { body: page([]) }
    })
    const user = userEvent.setup()
    renderApp('/leads/lead-1')

    await user.click(await screen.findByRole('button', { name: 'Registrar contato' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Esta mudança de status não é permitida')
  })

  it('descarte pede confirmação e envia CANCELLED', async () => {
    let sent: unknown
    mockFetch((method, url, body) => {
      if (url === '/api/auth/me') return { body: me('PROSPECTOR') }
      if (method === 'GET' && url === '/api/leads/lead-1') return { body: fakeLead({ status: 'CONTACTED' }) }
      if (method === 'PATCH' && url === '/api/leads/lead-1/status') {
        sent = body
        return { body: fakeLead({ status: 'CANCELLED' }) }
      }
      if (url.startsWith('/api/visits?')) return { body: page([]) }
    })
    const user = userEvent.setup()
    renderApp('/leads/lead-1')

    await user.click(await screen.findByRole('button', { name: 'Descartar Lead' }))
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Descartar' }))

    await waitFor(() => expect(sent).toEqual({ status: 'CANCELLED' }))
    expect(await screen.findByText('Descartado')).toBeInTheDocument()
  })

  describe('W3 — agendar visita e histórico de visitas', () => {
    const cases: { role: Role; status: LeadStatus; visible: boolean }[] = (['ADMIN', 'PROSPECTOR'] as Role[]).flatMap(
      (role) =>
        (['NEW', 'CONTACTED', 'VISITED', 'VISIT_SCHEDULED', 'CANCELLED'] as LeadStatus[]).map((status) => ({
          role,
          status,
          visible: status === 'NEW' || status === 'CONTACTED' || status === 'VISITED',
        })),
    )

    it.each(cases)('$role com Lead $status: botão visível = $visible', async ({ role, status, visible }) => {
      mockFetch((_method, url) => {
        if (url === '/api/auth/me') return { body: me(role) }
        if (url === '/api/leads/lead-1') return { body: fakeLead({ status }) }
        if (url.startsWith('/api/visits?')) return { body: page([]) }
      })
      renderApp('/leads/lead-1')

      expect(await screen.findByRole('heading', { name: 'Lead Fictício' })).toBeInTheDocument()
      if (visible) {
        expect(screen.getByRole('button', { name: 'Agendar visita' })).toBeInTheDocument()
      } else {
        expect(screen.queryByRole('button', { name: 'Agendar visita' })).not.toBeInTheDocument()
      }
    })

    it('Lead sem Prospector não oferece agendamento (RN03)', async () => {
      mockFetch((_method, url) => {
        if (url === '/api/auth/me') return { body: me('ADMIN') }
        if (url === '/api/leads/lead-1') return { body: fakeLead({ status: 'NEW', prospector: null }) }
        if (url.startsWith('/api/visits?')) return { body: page([]) }
      })
      renderApp('/leads/lead-1')

      expect(await screen.findByRole('heading', { name: 'Lead Fictício' })).toBeInTheDocument()
      expect(screen.queryByRole('button', { name: 'Agendar visita' })).not.toBeInTheDocument()
    })

    it('quem não pode escrever no Lead não chega ao botão: o backend responde 404 (D-041)', async () => {
      mockFetch((_method, url) => {
        if (url === '/api/auth/me') return { body: me('PROSPECTOR') }
        if (url === '/api/leads/lead-1') return problem(404, 'LEAD_NOT_FOUND')
      })
      renderApp('/leads/lead-1')

      expect(await screen.findByText('Lead não encontrado.')).toBeInTheDocument()
      expect(screen.queryByRole('button', { name: 'Agendar visita' })).not.toBeInTheDocument()
    })

    it('mostra o histórico de visitas do Lead, da mais recente para a mais antiga', async () => {
      const requested: string[] = []
      mockFetch((_method, url) => {
        if (url === '/api/auth/me') return { body: me('PROSPECTOR') }
        if (url === '/api/leads/lead-1') return { body: fakeLead({ status: 'VISIT_SCHEDULED' }) }
        if (url.startsWith('/api/visits?')) {
          requested.push(url)
          return {
            body: page([
              fakeVisit({ id: 'v-2', scheduledDate: '2026-10-10', status: 'SCHEDULED' }),
              fakeVisit({ id: 'v-1', scheduledDate: '2026-09-28', status: 'CANCELLED', canEdit: false }),
            ]),
          }
        }
      })
      renderApp('/leads/lead-1')

      const table = await screen.findByRole('table', { name: 'Histórico de visitas' })
      const rows = within(table).getAllByRole('row').slice(1)
      expect(rows.map((row) => row.textContent)).toEqual([
        '10/10/2026AgendadaProspector Fictício',
        '28/09/2026CanceladaProspector Fictício',
      ])
      expect(within(rows[0]).getByRole('link', { name: '10/10/2026' })).toHaveAttribute('href', '/visitas/v-2')
      expect(requested).toEqual(['/api/visits?page=0&leadId=lead-1&order=desc'])
    })
  })
})
