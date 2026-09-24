import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { me, mockFetch, renderApp } from '@/test/utils'
import type { User } from './api'

const gate: User = {
  id: 'u-1',
  name: 'Paulo Portaria',
  email: 'paulo@resort.local',
  role: 'GATE',
  active: true,
  mustChangePassword: false,
  createdAt: '2026-09-24T00:00:00Z',
  prospectorId: null,
  employeeCode: null,
  phone: null,
}

const page = { content: [gate], page: 0, size: 20, totalElements: 1, totalPages: 1 }

describe('senha temporária', () => {
  it('é exibida após a redefinição e some ao fechar', async () => {
    mockFetch((method, url) => {
      if (url === '/api/auth/me') return { body: me('ADMIN') }
      if (method === 'GET' && url.startsWith('/api/users')) return { body: page }
      if (method === 'POST' && url === '/api/users/u-1/reset-password') return { body: { temporaryPassword: 'Tmp2345abcde' } }
    })
    const user = userEvent.setup()
    renderApp('/usuarios')

    await user.click(await screen.findByRole('button', { name: 'Redefinir senha' }))
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Confirmar' }))

    expect(await screen.findByTestId('temporary-password')).toHaveTextContent('Tmp2345abcde')
    await user.click(screen.getByRole('button', { name: 'Fechar' }))
    await waitFor(() => expect(screen.queryByText('Tmp2345abcde')).not.toBeInTheDocument())
  })

  it('é exibida após a criação e some ao fechar', async () => {
    let created: unknown
    mockFetch((method, url, body) => {
      if (url === '/api/auth/me') return { body: me('ADMIN') }
      if (method === 'GET' && url.startsWith('/api/users')) return { body: page }
      if (method === 'POST' && url === '/api/users') {
        created = body
        return { status: 201, body: { user: { ...gate, id: 'u-2', name: 'Ana Anfitriã', role: 'HOST' }, temporaryPassword: 'Nova2345abcd' } }
      }
    })
    const user = userEvent.setup()
    renderApp('/usuarios')

    await user.click(await screen.findByRole('button', { name: 'Novo usuário' }))
    const dialog = await screen.findByRole('dialog')
    await user.type(within(dialog).getByLabelText('Nome'), 'Ana Anfitriã')
    await user.type(within(dialog).getByLabelText('E-mail'), 'ana@resort.local')
    await user.selectOptions(within(dialog).getByLabelText('Perfil'), 'HOST')
    await user.click(within(dialog).getByRole('button', { name: 'Criar usuário' }))

    expect(await screen.findByTestId('temporary-password')).toHaveTextContent('Nova2345abcd')
    expect(created).toEqual({ name: 'Ana Anfitriã', email: 'ana@resort.local', role: 'HOST' })
    await user.click(screen.getByRole('button', { name: 'Fechar' }))
    await waitFor(() => expect(screen.queryByText('Nova2345abcd')).not.toBeInTheDocument())
  })
})
