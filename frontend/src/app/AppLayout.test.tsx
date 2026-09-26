import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { usersQueryKey } from '@/features/users/api'
import { me, mockFetch, renderApp } from '@/test/utils'

describe('AppLayout — Sair', () => {
  it('chama o logout, limpa o cache e vai para o login', async () => {
    const user = userEvent.setup()
    const calls: string[] = []
    let loggedIn = true
    mockFetch((method, url) => {
      if (url === '/api/auth/me') return loggedIn ? { body: me('ADMIN') } : { status: 401, body: { code: 'UNAUTHENTICATED' } }
      if (method === 'POST' && url === '/api/auth/logout') {
        calls.push('logout')
        loggedIn = false
        return { status: 204 }
      }
      if (url.startsWith('/api/users')) return { body: { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 } }
    })
    const { router, queryClient } = renderApp('/usuarios')
    await screen.findByRole('heading', { name: 'Usuários' })
    expect(queryClient.getQueryCache().findAll({ queryKey: usersQueryKey })).not.toHaveLength(0)

    await user.click(screen.getByRole('button', { name: 'Usuário ADMIN' }))
    await user.click(await screen.findByRole('menuitem', { name: 'Sair' }))

    await waitFor(() => expect(router.state.location.pathname).toBe('/login'))
    expect(calls).toEqual(['logout'])
    // Os dados da sessão anterior não ficam na memória da aba (ex.: a lista de usuários).
    expect(queryClient.getQueryCache().findAll({ queryKey: usersQueryKey })).toHaveLength(0)
    expect(await screen.findByRole('heading', { name: 'Entrar' })).toBeInTheDocument()
  })
})
