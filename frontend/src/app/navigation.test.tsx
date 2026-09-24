import { screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { Role } from '@/features/auth/types'
import { me, mockFetch, renderApp } from '@/test/utils'

const cases: { role: Role; landing: string; heading: string; links: string[] }[] = [
  { role: 'ADMIN', landing: '/dashboard', heading: 'Dashboard', links: ['Dashboard', 'Usuários', 'Prospectores'] },
  { role: 'PROSPECTOR', landing: '/dashboard', heading: 'Dashboard', links: ['Dashboard', 'Perfil'] },
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

  it.each(['PROSPECTOR', 'GATE', 'HOST'] as Role[])('%s não acessa telas de administração', async (role) => {
    mockFetch((_method, url) => (url === '/api/auth/me' ? { body: me(role) } : undefined))
    const { router } = renderApp('/usuarios')

    await waitFor(() => expect(router.state.location.pathname).not.toBe('/usuarios'))
    expect(screen.queryByRole('heading', { name: 'Usuários' })).not.toBeInTheDocument()
  })
})
