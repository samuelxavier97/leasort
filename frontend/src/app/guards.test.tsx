import { screen, waitFor } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { me, mockFetch, renderApp, unauthenticated } from '@/test/utils'

describe('guards', () => {
  it.each(['/', '/usuarios', '/dashboard', '/trocar-senha'])('anônimo em %s vai para /login', async (path) => {
    mockFetch((_method, url) => (url === '/api/auth/me' ? unauthenticated : undefined))
    const { router } = renderApp(path)

    await waitFor(() => expect(router.state.location.pathname).toBe('/login'))
    expect(await screen.findByRole('heading', { name: 'Entrar' })).toBeInTheDocument()
  })

  it.each(['/', '/usuarios', '/dashboard', '/perfil'])(
    'com troca de senha pendente, %s vai para /trocar-senha',
    async (path) => {
      mockFetch((_method, url) =>
        url === '/api/auth/me' ? { body: me('ADMIN', { mustChangePassword: true }) } : undefined,
      )
      const { router } = renderApp(path)

      await waitFor(() => expect(router.state.location.pathname).toBe('/trocar-senha'))
      expect(await screen.findByText('Defina uma nova senha para continuar usando o sistema.')).toBeInTheDocument()
    },
  )

  it('sessão encerrada no servidor durante o uso volta ao login', async () => {
    let sessionAlive = true
    mockFetch((_method, url) => {
      if (url === '/api/auth/me') return sessionAlive ? { body: me('ADMIN') } : unauthenticated
      if (url.startsWith('/api/users')) {
        sessionAlive = false
        return unauthenticated
      }
    })
    const { router } = renderApp('/usuarios')

    await waitFor(() => expect(router.state.location.pathname).toBe('/login'))
  })
})
