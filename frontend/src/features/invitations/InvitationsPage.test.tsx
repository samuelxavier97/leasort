import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import type { Role } from '@/features/auth/types'
import { fakeInvitation } from '@/test/invitationFixtures'
import { page } from '@/test/leadFixtures'
import { me, mockFetch, renderApp } from '@/test/utils'

describe('C11 — lista de convites (§16.1)', () => {
  it.each(['ADMIN', 'PROSPECTOR'] as Role[])('%s abre nos convites ativos, em ordem de data, e filtra por status', async (role) => {
    const requested: string[] = []
    const first = fakeInvitation({ id: 'i-1' })
    const second = fakeInvitation({
      id: 'i-2',
      visit: { id: 'v-2', scheduledDate: '2026-10-05', status: 'SCHEDULED', companionsCount: 0, canEdit: true },
    })
    mockFetch((_method, url) => {
      if (url === '/api/auth/me') return { body: me(role) }
      if (url.startsWith('/api/invitations?')) {
        requested.push(url)
        return { body: page([first, second]) }
      }
    })
    const user = userEvent.setup()
    renderApp('/convites')

    expect(await screen.findByRole('heading', { name: 'Convites' })).toBeInTheDocument()
    await waitFor(() => expect(requested).toEqual(['/api/invitations?page=0&status=ACTIVE&order=asc']))
    const rows = within(screen.getByRole('table')).getAllByRole('row').slice(1)
    expect(rows.map((row) => row.textContent)).toEqual([
      `30/09/2026Lead Fictício${first.formattedCode}Ativo`,
      `05/10/2026Lead Fictício${second.formattedCode}Ativo`,
    ])
    expect(within(rows[0]).getByRole('link', { name: '30/09/2026' })).toHaveAttribute('href', '/convites/i-1')

    await user.selectOptions(screen.getByLabelText('Status'), 'Todos')
    await waitFor(() => expect(requested).toContain('/api/invitations?page=0&order=asc'))
  })
})
