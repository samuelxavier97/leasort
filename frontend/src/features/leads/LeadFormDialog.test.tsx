import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { formatCpfInput } from '@/lib/cpf'
import { fakeCpf } from '@/test/fakeCpf'
import { fakeLead } from '@/test/leadFixtures'
import { me, mockFetch, renderApp } from '@/test/utils'

async function openEdit() {
  const user = userEvent.setup()
  await user.click(await screen.findByRole('button', { name: 'Editar' }))
  return { user, dialog: await screen.findByRole('dialog') }
}

describe('formulário do Lead para o PROSPECTOR (D-061)', () => {
  it('CPF existente aparece mascarado, só leitura, e não é enviado', async () => {
    let sent: Record<string, unknown> | undefined
    mockFetch((method, url, body) => {
      if (url === '/api/auth/me') return { body: me('PROSPECTOR') }
      if (method === 'GET' && url === '/api/leads/lead-1') return { body: fakeLead() }
      if (method === 'PUT' && url === '/api/leads/lead-1') {
        sent = body as Record<string, unknown>
        return { body: fakeLead({ name: 'Nome Novo' }) }
      }
    })
    renderApp('/leads/lead-1')
    const { user, dialog } = await openEdit()

    const cpf = within(dialog).getByLabelText('CPF')
    expect(cpf).toHaveValue('***.456.789-**')
    expect(cpf).toBeDisabled()
    expect(within(dialog).getByText('Só o administrador altera o CPF.')).toBeInTheDocument()

    await user.clear(within(dialog).getByLabelText('Nome'))
    await user.type(within(dialog).getByLabelText('Nome'), 'Nome Novo')
    await user.click(within(dialog).getByRole('button', { name: 'Salvar' }))

    await waitFor(() => expect(sent).toBeDefined())
    expect(sent?.cpf).toBeNull()
  })

  it('Lead sem CPF permite informar e valida os dígitos', async () => {
    let sent: Record<string, unknown> | undefined
    mockFetch((method, url, body) => {
      if (url === '/api/auth/me') return { body: me('PROSPECTOR') }
      if (method === 'GET' && url === '/api/leads/lead-1') return { body: fakeLead({ cpf: null }) }
      if (method === 'PUT' && url === '/api/leads/lead-1') {
        sent = body as Record<string, unknown>
        return { body: fakeLead() }
      }
    })
    renderApp('/leads/lead-1')
    const { user, dialog } = await openEdit()

    const cpf = within(dialog).getByLabelText('CPF')
    expect(cpf).toBeEnabled()
    await user.type(cpf, '12345678901')
    expect(cpf).toHaveValue('123.456.789-01')
    await user.click(within(dialog).getByRole('button', { name: 'Salvar' }))
    expect(await within(dialog).findByText('CPF inválido.')).toBeInTheDocument()
    expect(sent).toBeUndefined()

    const valid = fakeCpf()
    await user.clear(cpf)
    await user.type(cpf, valid)
    await user.click(within(dialog).getByRole('button', { name: 'Salvar' }))
    await waitFor(() => expect(sent?.cpf).toBe(formatCpfInput(valid)))
  })
})
