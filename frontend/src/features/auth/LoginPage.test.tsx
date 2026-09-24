import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { me, mockFetch, problem, renderApp, unauthenticated } from '@/test/utils'

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
})
