import { screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { Role } from '@/features/auth/types'
import { me, mockFetch, renderApp } from '@/test/utils'

const cases: { role: Role; landing: string; heading: string; links: string[] }[] = [
  {
    role: 'ADMIN',
    landing: '/dashboard',
    heading: 'Dashboard',
    links: ['Dashboard', 'Leads', 'Prospectores', 'Visitas', 'Usuários'],
  },
    // W8: §16.1 — o PROSPECTOR ganha Agenda e Histórico; o ADMIN ganha Visitas.
  {
    role: 'PROSPECTOR',
    landing: '/dashboard',
    heading: 'Dashboard',
    links: ['Dashboard', 'Meus Leads', 'Agenda', 'Histórico', 'Perfil'],
  },
  { role: 'GATE', landing: '/portaria', heading: 'Validar Convite', links: ['Validar Convite'] },
  { role: 'HOST', landing: '/chegadas', heading: 'Chegadas de hoje', links: ['Chegadas de hoje'] },
]

describe('página inicial e menu por perfil', () => {
  it.each(cases)('$role cai em $landing e vê só o seu menu', async ({ role, landing, heading, links }) => {
    mockFetch((_method, url) => (url === '/api/auth/me' ? { body: me(role) } : undefined))
    const { router } = renderApp('/')

    await waitFor(() => expect(router.state.location.pathname).toBe(landing))
    expect(await screen.findByRole('heading', { name: heading })).toBeInTheDocument()
    const nav = screen.getByRole('navigation', { name: 'Menu principal' })
    expect(within(nav).getAllByRole('link').map((link) => link.textContent)).toEqual(links)
  })

  it.each(['GATE', 'HOST'] as Role[])('%s não acessa Leads', async (role) => {
    mockFetch((_method, url) => (url === '/api/auth/me' ? { body: me(role) } : undefined))
    const { router } = renderApp('/leads')

    await waitFor(() => expect(router.state.location.pathname).not.toBe('/leads'))
  })

  it.each(['/agenda', '/historico'])('ADMIN não acessa %s, que é do PROSPECTOR', async (path) => {
    mockFetch((_method, url) => (url === '/api/auth/me' ? { body: me('ADMIN') } : undefined))
    const { router } = renderApp(path)

    await waitFor(() => expect(router.state.location.pathname).toBe('/dashboard'))
  })

  it('PROSPECTOR não acessa a lista de visitas do ADMIN', async () => {
    mockFetch((_method, url) => (url === '/api/auth/me' ? { body: me('PROSPECTOR') } : undefined))
    const { router } = renderApp('/visitas')

    await waitFor(() => expect(router.state.location.pathname).toBe('/dashboard'))
  })

  it.each(['GATE', 'HOST'] as Role[])('%s não acessa visitas', async (role) => {
    mockFetch((_method, url) => (url === '/api/auth/me' ? { body: me(role) } : undefined))
    const { router } = renderApp('/visitas/v-1')

    await waitFor(() => expect(router.state.location.pathname).not.toBe('/visitas/v-1'))
  })

  it('PROSPECTOR não acessa a importação', async () => {
    mockFetch((_method, url) => (url === '/api/auth/me' ? { body: me('PROSPECTOR') } : undefined))
    const { router } = renderApp('/leads/importar')

    await waitFor(() => expect(router.state.location.pathname).toBe('/dashboard'))
  })

  it.each(['PROSPECTOR', 'GATE', 'HOST'] as Role[])('%s não acessa telas de administração', async (role) => {
    mockFetch((_method, url) => (url === '/api/auth/me' ? { body: me(role) } : undefined))
    const { router } = renderApp('/usuarios')

    await waitFor(() => expect(router.state.location.pathname).not.toBe('/usuarios'))
    expect(screen.queryByRole('heading', { name: 'Usuários' })).not.toBeInTheDocument()
  })
})
