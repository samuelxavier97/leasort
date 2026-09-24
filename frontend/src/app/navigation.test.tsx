import { screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { Role } from '@/features/auth/types'
import { me, mockFetch, renderApp } from '@/test/utils'

const cases: { role: Role; landing: string; heading: string; links: string[] }[] = [
  {
    role: 'ADMIN',
    landing: '/dashboard',
    heading: 'Dashboard',
    links: ['Dashboard', 'Leads', 'Prospectores', 'Visitas', 'Convites', 'Chegadas', 'Acessos', 'Usuários'],
  },
    // W8 e C11: §16.1 — o PROSPECTOR ganha Agenda, Convites e Histórico; o ADMIN ganha Visitas e Convites.
  {
    role: 'PROSPECTOR',
    landing: '/dashboard',
    heading: 'Dashboard',
    links: ['Dashboard', 'Meus Leads', 'Agenda', 'Convites', 'Chegadas de hoje', 'Histórico', 'Perfil'],
  },
  // P1: o GATE cai em /portaria com "Validar Convite" e "Acessos Recentes"; o ADMIN ganha "Acessos".
  { role: 'GATE', landing: '/portaria', heading: 'Validar Convite', links: ['Validar Convite', 'Acessos Recentes'] },
  // F1: o HOST só tem "Chegadas de hoje"; o PROSPECTOR ganha "Chegadas de hoje" e o ADMIN "Chegadas" (§16.1).
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

  it.each(['GATE', 'HOST'] as Role[])('%s não acessa convites', async (role) => {
    mockFetch((_method, url) => (url === '/api/auth/me' ? { body: me(role) } : undefined))
    const { router } = renderApp('/convites/i-1')

    await waitFor(() => expect(router.state.location.pathname).not.toBe('/convites/i-1'))
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

  it.each(['ADMIN', 'PROSPECTOR', 'HOST'] as Role[])('%s não acessa /portaria', async (role) => {
    mockFetch((_method, url) => (url === '/api/auth/me' ? { body: me(role) } : undefined))
    const { router } = renderApp('/portaria')

    await waitFor(() => expect(router.state.location.pathname).not.toBe('/portaria'))
    expect(screen.queryByRole('button', { name: 'ESCANEAR QR CODE' })).not.toBeInTheDocument()
  })

  it.each([
    { role: 'GATE' as Role, heading: 'Acessos Recentes' },
    { role: 'ADMIN' as Role, heading: 'Acessos' },
  ])('$role abre /acessos com o título "$heading"', async ({ role, heading }) => {
    mockFetch((_method, url) => {
      if (url === '/api/auth/me') return { body: me(role) }
      if (url === '/api/access/recent') return { body: [] }
    })
    const { router } = renderApp('/acessos')

    expect(await screen.findByRole('heading', { name: heading })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/acessos')
  })

  it.each(['PROSPECTOR', 'HOST'] as Role[])('%s não acessa /acessos', async (role) => {
    mockFetch((_method, url) => (url === '/api/auth/me' ? { body: me(role) } : undefined))
    const { router } = renderApp('/acessos')

    await waitFor(() => expect(router.state.location.pathname).not.toBe('/acessos'))
  })

  it.each(['/chegadas', '/chegadas/v-1/ficha'])('GATE não acessa %s', async (path) => {
    mockFetch((_method, url) => (url === '/api/auth/me' ? { body: me('GATE') } : undefined))
    const { router } = renderApp(path)

    await waitFor(() => expect(router.state.location.pathname).toBe('/portaria'))
  })

  it.each(['/leads', '/visitas/v-1', '/convites'])('HOST não acessa %s', async (path) => {
    mockFetch((_method, url) => (url === '/api/auth/me' ? { body: me('HOST') } : undefined))
    const { router } = renderApp(path)

    await waitFor(() => expect(router.state.location.pathname).toBe('/chegadas'))
  })
})
