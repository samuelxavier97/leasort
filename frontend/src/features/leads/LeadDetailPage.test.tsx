import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { fakeLead } from '@/test/leadFixtures'
import { me, mockFetch, problem, renderApp } from '@/test/utils'

describe('LeadDetailPage', () => {
  it('mostra as ações conforme status e perfil', async () => {
    mockFetch((_method, url) => {
      if (url === '/api/auth/me') return { body: me('ADMIN') }
      if (url === '/api/leads/lead-1') return { body: fakeLead({ status: 'CANCELLED' }) }
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
    })
    const user = userEvent.setup()
    renderApp('/leads/lead-1')

    await user.click(await screen.findByRole('button', { name: 'Descartar Lead' }))
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Descartar' }))

    await waitFor(() => expect(sent).toEqual({ status: 'CANCELLED' }))
    expect(await screen.findByText('Descartado')).toBeInTheDocument()
  })
})
