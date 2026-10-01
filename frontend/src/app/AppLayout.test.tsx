import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { usersQueryKey } from '@/features/users/api'
import { applyBrand, currentBrand } from '@/lib/brand'
import { me, mockFetch, renderApp, setBrand } from '@/test/utils'

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

const RESORT = 'Resort Fictício das Águas'

function mockAdmin() {
  mockFetch((_method, url) => {
    if (url === '/api/auth/me') return { body: me('ADMIN') }
    if (url.startsWith('/api/users')) return { body: { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 } }
  })
}

/** Identidade no cabeçalho: o link para a tela inicial, antes do menu. */
async function headerIdentity() {
  await screen.findByRole('heading', { name: 'Usuários' })
  return screen.getByRole('banner').querySelector('a[href="/"]')!
}

describe('AppLayout — identidade do cliente (D-117)', () => {
  it('L1: sem tema, o cabeçalho mostra o logotipo do Resortric', async () => {
    mockAdmin()
    renderApp('/usuarios')
    const identity = await headerIdentity()
    const image = within(identity as HTMLElement).getByRole('img')
    expect(image).toHaveAttribute('src', '/resortric.svg')
    expect(image).toHaveAccessibleName('Resortric')
  })

  it('L2: só com RESORT_NAME, o cabeçalho mostra o nome em texto, nunca "Resortric"', async () => {
    setBrand({ name: RESORT, color: '', logo: '' })
    mockAdmin()
    renderApp('/usuarios')
    const identity = await headerIdentity()
    expect(identity).toHaveTextContent(RESORT)
    expect(identity.querySelector('img')).toBeNull()
    expect(within(screen.getByRole('banner')).queryByRole('img', { name: 'Resortric' })).not.toBeInTheDocument()
  })

  it('L3: com nome e logotipo, mostra só o logotipo, por <img>, com o nome no alt', async () => {
    setBrand({ name: RESORT, color: '#1e3a5f', logo: '/brand/logo.png' })
    mockAdmin()
    renderApp('/usuarios')
    const identity = await headerIdentity()
    const image = within(identity as HTMLElement).getByRole('img')
    expect(image).toHaveAttribute('src', '/brand/logo.png')
    expect(image).toHaveAccessibleName(RESORT)
    expect(identity).not.toHaveTextContent(RESORT)
    // Nunca inline (D-119): nenhum <svg> no cabeçalho.
    expect(screen.getByRole('banner').querySelector('svg:not(.lucide)')).toBeNull()
  })

  it('L4: só com o logotipo, o alt é "Logotipo do Resort"', async () => {
    setBrand({ logo: '/brand/logo.png' })
    mockAdmin()
    renderApp('/usuarios')
    const identity = await headerIdentity()
    expect(within(identity as HTMLElement).getByRole('img')).toHaveAccessibleName('Logotipo do Resort')
  })

  it.each([
    ['sem tema', {}, '/resortric.svg'],
    ['só com RESORT_NAME', { name: RESORT }, '/resortric.svg'],
    ['com cor de cliente', { name: RESORT, color: '#1e3a5f' }, '/resortric-mono.svg'],
    ['com logotipo', { name: RESORT, logo: '/brand/logo.png' }, '/resortric-mono.svg'],
  ])('L5: o rodapé traz o Resortric com qualquer tema, sem dourado com tema de cliente (D-126): %s', async (_case, theme, src) => {
    setBrand(theme)
    mockAdmin()
    renderApp('/usuarios')
    await headerIdentity()
    expect(within(screen.getByRole('contentinfo')).getByRole('img', { name: 'Resortric' })).toHaveAttribute('src', src)
  })

  it('L9: a faixa do topo termina no trecho de detalhe (--brand-accent), que é o dourado só sem tema de cliente (D-126)', async () => {
    mockAdmin()
    renderApp('/usuarios')
    await headerIdentity()
    const stripe = screen.getByTestId('brand-stripe')
    expect(stripe).toHaveClass('h-1', 'bg-primary', 'print:hidden')
    expect(stripe.firstElementChild).toHaveClass('bg-brand-accent')
  })

  it('L7: numa instalação de demonstração, o rodapé mostra a etiqueta ao lado do Resortric (D-125)', async () => {
    setBrand({ name: RESORT, demo: 'true' })
    mockAdmin()
    renderApp('/usuarios')
    await headerIdentity()
    const footer = screen.getByRole('contentinfo')
    expect(within(footer).getByRole('img', { name: 'Resortric' })).toBeInTheDocument()
    expect(within(footer).getByText('Ambiente de demonstração')).toBeInTheDocument()
    // Fora do rodapé, a etiqueta não aparece; o rodapé some na impressão.
    expect(screen.getAllByText('Ambiente de demonstração')).toHaveLength(1)
    expect(footer).toHaveClass('print:hidden')
  })

  it.each([undefined, '', 'false', 'TRUE'])('L8: sem a meta resort-demo igual a "true" (%s), nenhuma etiqueta', async (demo) => {
    setBrand({ demo })
    mockAdmin()
    renderApp('/usuarios')
    await headerIdentity()
    expect(within(screen.getByRole('contentinfo')).getByRole('img', { name: 'Resortric' })).toBeInTheDocument()
    expect(screen.queryByText('Ambiente de demonstração')).not.toBeInTheDocument()
  })

  it('L6: título da aba por tela: "Tela · RESORT_NAME", ou "Tela · Resortric" sem o nome', async () => {
    mockAdmin()
    const { unmount } = renderApp('/usuarios')
    await headerIdentity()
    await waitFor(() => expect(document.title).toBe('Usuários · Resortric'))
    unmount()

    setBrand({ name: RESORT, logo: '/brand/logo.png' })
    renderApp('/usuarios')
    await headerIdentity()
    await waitFor(() => expect(document.title).toBe(`Usuários · ${RESORT}`))
  })

  it('L7: o item ativo do menu tem fundo, negrito e a linha na cor de borda, também com cor clara', async () => {
    setBrand({ color: '#f5d90a' })
    applyBrand(document.documentElement, currentBrand())
    mockAdmin()
    renderApp('/usuarios')
    await headerIdentity()
    const menu = screen.getByRole('navigation', { name: 'Menu principal' })
    // Usuários fica no submenu "Administração" (D-128): o botão dele mostra que a tela atual está lá dentro.
    const group = within(menu).getByRole('button', { name: 'Administração' })
    expect(group).toHaveClass('bg-soft-strong', 'font-semibold', 'border-primary-edge')
    const user = userEvent.setup()
    await user.click(group)
    const active = await screen.findByRole('menuitem', { name: 'Usuários' })
    expect(active).toHaveAttribute('aria-current', 'page')
    expect(active).toHaveClass('bg-soft-strong', 'font-semibold', 'border-primary-edge')
    expect(screen.getAllByRole('menuitem').map((item) => item.textContent)).toEqual(['Exportações', 'Usuários', 'Auditoria'])
    await user.keyboard('{Escape}')
    const inactive = within(menu).getByRole('link', { name: 'Leads' })
    expect(inactive).not.toHaveClass('font-semibold')
    expect(inactive).not.toHaveClass('border-primary-edge')
    // A linha usa a borda da D-118: escurecida até 3:1 para a cor clara, não a própria cor.
    expect(document.documentElement.style.getPropertyValue('--primary-edge')).not.toBe('#f5d90a')
  })

  it('L10: o PROSPECTOR não tem o submenu "Administração" (D-128)', async () => {
    mockFetch((_method, url) => (url === '/api/auth/me' ? { body: me('PROSPECTOR') } : undefined))
    renderApp('/perfil')
    const menu = await screen.findByRole('navigation', { name: 'Menu principal' })
    expect(within(menu).queryByRole('button', { name: 'Administração' })).not.toBeInTheDocument()
  })
})

describe('AppLayout — menu do celular (A8, D-122)', () => {
  function mockGate(onLogout?: () => void) {
    let loggedIn = true
    mockFetch((method, url) => {
      if (url === '/api/auth/me') return loggedIn ? { body: me('GATE') } : { status: 401, body: { code: 'UNAUTHENTICATED' } }
      if (method === 'POST' && url === '/api/auth/logout') {
        loggedIn = false
        onLogout?.()
        return { status: 204 }
      }
      if (url === '/api/access/recent') return { body: [] }
    })
  }

  it('o botão Menu traz o usuário, os itens do perfil com o ativo marcado, Trocar senha e Sair', async () => {
    const user = userEvent.setup()
    mockGate()
    renderApp('/portaria')
    await screen.findByRole('heading', { name: 'Validar Convite' })

    await user.click(screen.getByRole('button', { name: 'Menu' }))
    const menu = await screen.findByRole('menu')
    expect(within(menu).getByText('Usuário GATE')).toBeInTheDocument()
    expect(within(menu).getByText('Portaria')).toBeInTheDocument()
    const items = within(menu).getAllByRole('menuitem').map((item) => item.textContent)
    expect(items).toEqual(['Validar Convite', 'Acessos Recentes', 'Trocar senha', 'Sair'])
    const active = within(menu).getByRole('menuitem', { name: 'Validar Convite' })
    expect(active).toHaveAttribute('aria-current', 'page')
    expect(active).toHaveClass('font-semibold', 'border-primary-edge')
  })

  it('um item do Menu navega; Sair pelo Menu encerra a sessão', async () => {
    const user = userEvent.setup()
    let loggedOut = false
    mockGate(() => (loggedOut = true))
    const { router } = renderApp('/portaria')
    await screen.findByRole('heading', { name: 'Validar Convite' })

    await user.click(screen.getByRole('button', { name: 'Menu' }))
    await user.click(await screen.findByRole('menuitem', { name: 'Acessos Recentes' }))
    await waitFor(() => expect(router.state.location.pathname).toBe('/acessos'))

    await user.click(screen.getByRole('button', { name: 'Menu' }))
    await user.click(await screen.findByRole('menuitem', { name: 'Sair' }))
    await waitFor(() => expect(router.state.location.pathname).toBe('/login'))
    expect(loggedOut).toBe(true)
  })

  it('no celular, o menu do cabeçalho e o usuário ficam escondidos e o botão Menu aparece; no computador, o contrário', async () => {
    mockGate()
    renderApp('/portaria')
    await screen.findByRole('heading', { name: 'Validar Convite' })
    expect(screen.getByRole('navigation', { name: 'Menu principal' })).toHaveClass('hidden', 'md:flex')
    expect(screen.getByRole('button', { name: 'Usuário GATE' }).parentElement).toHaveClass('hidden', 'md:block')
    expect(screen.getByRole('button', { name: 'Menu' }).parentElement).toHaveClass('md:hidden')
  })
})

