import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { me, mockFetch, problem, renderApp, setBrand, unauthenticated } from '@/test/utils'

async function fillAndSubmit(email: string, password: string) {
  const user = userEvent.setup()
  if (email) await user.type(await screen.findByLabelText('E-mail'), email)
  if (password) await user.type(screen.getByLabelText('Senha'), password)
  await user.click(screen.getByRole('button', { name: 'Entrar' }))
}

describe('LoginPage', () => {
  it('mostra mensagens de validação em português', async () => {
    mockFetch((_method, url) => (url === '/api/auth/me' ? unauthenticated : undefined))
    renderApp('/login')

    await fillAndSubmit('', '')

    expect(await screen.findByText('Informe o e-mail.')).toBeInTheDocument()
    expect(screen.getByText('Informe a senha.')).toBeInTheDocument()
  })

  it('valida o formato do e-mail', async () => {
    mockFetch((_method, url) => (url === '/api/auth/me' ? unauthenticated : undefined))
    renderApp('/login')

    await fillAndSubmit('nao-e-email', 'qualquer-senha')

    expect(await screen.findByText('E-mail inválido.')).toBeInTheDocument()
  })

  it('mostra mensagem genérica para credenciais inválidas', async () => {
    mockFetch((_method, url) => {
      if (url === '/api/auth/me') return unauthenticated
      if (url === '/api/auth/login') return problem(401, 'INVALID_CREDENTIALS')
    })
    renderApp('/login')

    await fillAndSubmit('gate@resort.local', 'senha-errada-1')

    expect(await screen.findByRole('alert')).toHaveTextContent('E-mail ou senha inválidos.')
  })

  it('mostra a mensagem de bloqueio quando o limite de tentativas estoura', async () => {
    mockFetch((_method, url) => {
      if (url === '/api/auth/me') return unauthenticated
      if (url === '/api/auth/login') return problem(429, 'TOO_MANY_LOGIN_ATTEMPTS')
    })
    renderApp('/login')

    await fillAndSubmit('gate@resort.local', 'senha-qualquer')

    expect(await screen.findByRole('alert')).toHaveTextContent('Muitas tentativas de login')
  })

  it('após o login leva à tela principal do perfil', async () => {
    mockFetch((_method, url) => {
      if (url === '/api/auth/me') return unauthenticated
      if (url === '/api/auth/login') return { body: me('HOST') }
    })
    const { router } = renderApp('/login')

    await fillAndSubmit('host@resort.local', 'senha-correta-1')

    await waitFor(() => expect(router.state.location.pathname).toBe('/chegadas'))
    expect(await screen.findByRole('heading', { name: 'Chegadas de hoje' })).toBeInTheDocument()
  })

  it('título da aba: "Entrar · Resortric" sem tema e "Entrar · RESORT_NAME" com o nome (D-117)', async () => {
    mockFetch((_method, url) => (url === '/api/auth/me' ? unauthenticated : undefined))
    const { unmount } = renderApp('/login')
    await screen.findByRole('heading', { name: 'Entrar' })
    await waitFor(() => expect(document.title).toBe('Entrar · Resortric'))
    unmount()

    setBrand({ name: 'Resort Fictício das Águas' })
    renderApp('/login')
    await screen.findByRole('heading', { name: 'Entrar' })
    await waitFor(() => expect(document.title).toBe('Entrar · Resort Fictício das Águas'))
  })

  describe('identidade no login (D-117, D-121)', () => {
    const RESORT = 'Resort Fictício das Águas'
    async function identity() {
      mockFetch((_method, url) => (url === '/api/auth/me' ? unauthenticated : undefined))
      renderApp('/login')
      await screen.findByRole('heading', { name: 'Entrar' })
      return screen.getByTestId('auth-identity')
    }

    it('sem tema: o logotipo do Resortric em destaque', async () => {
      const box = await identity()
      expect(within(box).getByRole('img', { name: 'Resortric' })).toHaveAttribute('src', '/resortric.svg')
    })

    it('só com RESORT_NAME: o nome em texto, nunca "Resortric" no destaque', async () => {
      setBrand({ name: RESORT })
      const box = await identity()
      expect(box).toHaveTextContent(RESORT)
      expect(within(box).queryByRole('img')).not.toBeInTheDocument()
    })

    it('com nome e logotipo: só o logotipo, por <img>, com o nome no alt', async () => {
      setBrand({ name: RESORT, logo: '/brand/logo.png' })
      const box = await identity()
      const image = within(box).getByRole('img')
      expect(image).toHaveAttribute('src', '/brand/logo.png')
      expect(image).toHaveAccessibleName(RESORT)
      expect(box).not.toHaveTextContent(RESORT)
      expect(box.querySelector('svg')).toBeNull()
    })

    it('só com o logotipo: alt "Logotipo do Resort"', async () => {
      setBrand({ logo: '/brand/logo.png' })
      const box = await identity()
      expect(within(box).getByRole('img')).toHaveAccessibleName('Logotipo do Resort')
    })

    it('o rodapé traz o Resortric com qualquer tema', async () => {
      setBrand({ name: RESORT, logo: '/brand/logo.png' })
      await identity()
      expect(within(screen.getByRole('contentinfo')).getByRole('img', { name: 'Resortric' })).toHaveAttribute('src', '/resortric.svg')
    })

    it('instalação de demonstração: o rodapé do login mostra a etiqueta; sem ela, nada (D-125)', async () => {
      setBrand({ demo: 'true' })
      await identity()
      expect(within(screen.getByRole('contentinfo')).getByText('Ambiente de demonstração')).toBeInTheDocument()
    })

    it('sem instalação de demonstração, o login não mostra a etiqueta', async () => {
      await identity()
      expect(screen.queryByText('Ambiente de demonstração')).not.toBeInTheDocument()
    })
  })
})
