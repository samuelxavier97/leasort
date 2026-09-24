import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { me, mockFetch, problem, renderApp } from '@/test/utils'

async function fill(current: string, next: string, confirm: string) {
  const user = userEvent.setup()
  await user.type(await screen.findByLabelText('Senha atual'), current)
  await user.type(screen.getByLabelText('Nova senha'), next)
  await user.type(screen.getByLabelText('Confirme a nova senha'), confirm)
  await user.click(screen.getByRole('button', { name: 'Salvar nova senha' }))
}

describe('ChangePasswordPage', () => {
  it('exige no mínimo 10 caracteres', async () => {
    mockFetch((_method, url) => (url === '/api/auth/me' ? { body: me('GATE', { mustChangePassword: true }) } : undefined))
    renderApp('/trocar-senha')

    await fill('senha-temporaria', 'curta', 'curta')

    expect(await screen.findByText('A senha deve ter no mínimo 10 caracteres.')).toBeInTheDocument()
  })

  it('exige que a confirmação seja igual', async () => {
    mockFetch((_method, url) => (url === '/api/auth/me' ? { body: me('GATE', { mustChangePassword: true }) } : undefined))
    renderApp('/trocar-senha')

    await fill('senha-temporaria', 'nova-senha-segura', 'outra-senha-segura')

    expect(await screen.findByText('As senhas não conferem.')).toBeInTheDocument()
  })

  it('mostra o erro do backend para senha atual incorreta', async () => {
    mockFetch((_method, url) => {
      if (url === '/api/auth/me') return { body: me('GATE', { mustChangePassword: true }) }
      if (url === '/api/auth/change-password') return problem(400, 'INVALID_CURRENT_PASSWORD')
    })
    renderApp('/trocar-senha')

    await fill('senha-errada-1', 'nova-senha-segura', 'nova-senha-segura')

    expect(await screen.findByRole('alert')).toHaveTextContent('Senha atual incorreta.')
  })

  it('após a troca leva à tela principal do perfil', async () => {
    let body: unknown
    mockFetch((_method, url, requestBody) => {
      if (url === '/api/auth/me') return { body: me('GATE', { mustChangePassword: true }) }
      if (url === '/api/auth/change-password') {
        body = requestBody
        return { status: 204 }
      }
    })
    const { router } = renderApp('/trocar-senha')

    await fill('senha-temporaria', 'nova-senha-segura', 'nova-senha-segura')

    await waitFor(() => expect(router.state.location.pathname).toBe('/portaria'))
    expect(body).toEqual({ currentPassword: 'senha-temporaria', newPassword: 'nova-senha-segura' })
  })
})
