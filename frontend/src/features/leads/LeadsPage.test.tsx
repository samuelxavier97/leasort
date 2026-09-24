import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { fakeLead, page } from '@/test/leadFixtures'
import { me, mockFetch, renderApp } from '@/test/utils'

const prospectors = page([
  { id: 'p-1', userId: 'u-1', name: 'Ana Prospectora', email: 'ana@test.local', active: true, employeeCode: 'P-1', phone: null },
  { id: 'p-2', userId: 'u-2', name: 'Bruno Inativo', email: 'bruno@test.local', active: false, employeeCode: 'P-2', phone: null },
])

describe('LeadsPage', () => {
  it('PROSPECTOR vê só a própria lista, sem seleção, atribuição, importação nem filtro de Prospector', async () => {
    mockFetch((_method, url) => {
      if (url === '/api/auth/me') return { body: me('PROSPECTOR') }
      if (url.startsWith('/api/leads')) return { body: page([fakeLead()]) }
    })
    renderApp('/leads')

    expect(await screen.findByRole('heading', { name: 'Meus Leads' })).toBeInTheDocument()
    expect(await screen.findByText('***.456.789-**')).toBeInTheDocument()
    expect(screen.queryByRole('checkbox')).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Importar CSV' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Novo Lead' })).not.toBeInTheDocument()
    expect(screen.queryByLabelText('Prospector')).not.toBeInTheDocument()
  })

  it('ADMIN vê seleção, importação, criação e filtro de Prospector', async () => {
    mockFetch((_method, url) => {
      if (url === '/api/auth/me') return { body: me('ADMIN') }
      if (url.startsWith('/api/prospectors')) return { body: prospectors }
      if (url.startsWith('/api/leads')) return { body: page([fakeLead({ cpf: '123.456.789-01' })]) }
    })
    renderApp('/leads')

    expect(await screen.findByRole('heading', { name: 'Leads' })).toBeInTheDocument()
    expect(await screen.findByRole('checkbox', { name: 'Selecionar Lead Fictício' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Importar CSV' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Novo Lead' })).toBeInTheDocument()
    expect(screen.getByLabelText('Prospector')).toBeInTheDocument()
  })

  it('atribuição em lote envia os Leads selecionados e o Prospector escolhido', async () => {
    let sent: unknown
    mockFetch((method, url, body) => {
      if (url === '/api/auth/me') return { body: me('ADMIN') }
      if (url.startsWith('/api/prospectors')) return { body: prospectors }
      if (method === 'PATCH' && url === '/api/leads/assign') {
        sent = body
        return { body: { assigned: 2 } }
      }
      if (url.startsWith('/api/leads')) {
        return { body: page([fakeLead({ id: 'l-1', name: 'Lead Um' }), fakeLead({ id: 'l-2', name: 'Lead Dois' })]) }
      }
    })
    const user = userEvent.setup()
    renderApp('/leads')

    await user.click(await screen.findByRole('checkbox', { name: 'Selecionar Lead Um' }))
    await user.click(screen.getByRole('checkbox', { name: 'Selecionar Lead Dois' }))
    const bar = screen.getByLabelText('Atribuição em lote')
    const select = within(bar).getByLabelText('Atribuir a')
    expect(within(select).queryByRole('option', { name: /Bruno Inativo/ })).not.toBeInTheDocument()
    await user.selectOptions(select, 'p-1')
    await user.click(within(bar).getByRole('button', { name: 'Atribuir' }))

    await waitFor(() => expect(sent).toEqual({ leadIds: ['l-1', 'l-2'], prospectorId: 'p-1' }))
    await waitFor(() => expect(screen.queryByLabelText('Atribuição em lote')).not.toBeInTheDocument())
  })
})
