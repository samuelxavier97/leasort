import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { Role } from '@/features/auth/types'
import type { Visit } from './api'
import { fakeCpf } from '@/test/fakeCpf'
import { page } from '@/test/leadFixtures'
import { me, mockFetch, problem, renderApp, type Handler } from '@/test/utils'
import { fakeCompanion, fakeVisit } from '@/test/visitFixtures'

// Hoje = 24/09/2026 no fuso da operação; limite de data 24/09/2027.
beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] })
  vi.setSystemTime(new Date('2026-09-24T15:00:00Z'))
})

afterEach(() => {
  vi.useRealTimers()
})

const ACTIONS = ['Editar', 'Remarcar', 'Cancelar visita']

function mockVisit(role: Role, visit: Visit, extra?: Handler) {
  return mockFetch((method, url, body) => {
    if (url === '/api/auth/me') return { body: me(role) }
    const handled = extra?.(method, url, body)
    if (handled) return handled
    if (method === 'GET' && url === `/api/visits/${visit.id}`) return { body: visit }
    if (method === 'GET' && url.startsWith('/api/visits?')) return { body: page([]) }
  })
}

describe('W4 — ações do detalhe da visita dependem de canEdit (D-078)', () => {
  it('visita SCHEDULED com canEdit: Editar, Remarcar e Cancelar visita', async () => {
    mockVisit('PROSPECTOR', fakeVisit())
    renderApp('/visitas/v-1')

    expect(await screen.findByRole('heading', { name: 'Visita de 30/09/2026' })).toBeInTheDocument()
    for (const action of ACTIONS) {
      expect(screen.getByRole('button', { name: action })).toBeInTheDocument()
    }
  })

  it('quem só lê (responsável antigo) não vê ação nenhuma', async () => {
    mockVisit('PROSPECTOR', fakeVisit({ canEdit: false }))
    renderApp('/visitas/v-1')

    expect(await screen.findByText('Agendada')).toBeInTheDocument()
    for (const action of ACTIONS) {
      expect(screen.queryByRole('button', { name: action })).not.toBeInTheDocument()
    }
  })

  it.each(['PROSPECTOR', 'ADMIN'] as Role[])(
    'visita CANCELLED vista por quem escreve (%s, dono atual ou ADMIN) não tem ações: o backend manda canEdit false',
    async (role) => {
      mockVisit(role, fakeVisit({ status: 'CANCELLED', cancelledAt: '2026-09-25T02:30:00Z', canEdit: false }))
      renderApp('/visitas/v-1')

      expect(await screen.findByText('Cancelada')).toBeInTheDocument()
      // Instante UTC exibido no fuso da operação: 02:30Z de 25/09 ainda é 24/09 em São Paulo.
      expect(screen.getByText('24/09/2026')).toBeInTheDocument()
      for (const action of ACTIONS) {
        expect(screen.queryByRole('button', { name: action })).not.toBeInTheDocument()
      }
    },
  )

  it('visita inexistente ou fora da carteira mostra "Visita não encontrada."', async () => {
    mockFetch((_method, url) => {
      if (url === '/api/auth/me') return { body: me('PROSPECTOR') }
      if (url === '/api/visits/v-9') return problem(404, 'VISIT_NOT_FOUND')
    })
    renderApp('/visitas/v-9')

    expect(await screen.findByText('Visita não encontrada.')).toBeInTheDocument()
  })
})

describe('W5 e W10 — edição de acompanhantes', () => {
  const masked = '***.456.789-**'

  it('W5: para o PROSPECTOR, CPF existente fica mascarado e só leitura; o vazio é editável e validado', async () => {
    let sent: unknown
    const visit = fakeVisit({
      companions: [
        fakeCompanion({ id: 'c-1', name: 'Com CPF', cpf: masked }),
        fakeCompanion({ id: 'c-2', name: 'Sem CPF', cpf: null, relationship: 'FRIEND' }),
      ],
    })
    mockVisit('PROSPECTOR', visit, (method, url, body) => {
      if (method === 'PUT' && url === '/api/visits/v-1') {
        sent = body
        return { body: visit }
      }
    })
    const cpf = fakeCpf()
    const user = userEvent.setup()
    renderApp('/visitas/v-1')

    // A tabela do detalhe também mostra só a máscara.
    expect(await screen.findByText(masked)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Editar' }))
    const dialog = await screen.findByRole('dialog', { name: 'Editar visita' })

    const locked = within(dialog).getByLabelText('CPF do acompanhante 1 (opcional)')
    expect(locked).toHaveValue(masked)
    expect(locked).toHaveAttribute('readonly')
    expect(locked).toBeDisabled()
    expect(within(dialog).getByText('Só o administrador altera o CPF.')).toBeInTheDocument()

    const empty = within(dialog).getByLabelText('CPF do acompanhante 2 (opcional)')
    expect(empty).toBeEnabled()
    await user.type(empty, '12345678900')
    await user.click(within(dialog).getByRole('button', { name: 'Salvar' }))
    expect(await within(dialog).findByText('CPF inválido.')).toBeInTheDocument()
    expect(sent).toBeUndefined()

    await user.clear(empty)
    await user.type(empty, cpf)
    await user.click(within(dialog).getByRole('button', { name: 'Salvar' }))

    const formatted = `${cpf.slice(0, 3)}.${cpf.slice(3, 6)}.${cpf.slice(6, 9)}-${cpf.slice(9)}`
    await waitFor(() =>
      expect(sent).toEqual({
        notes: null,
        hostNotes: null,
        companions: [
          // CPF bloqueado vai como null: o backend mantém o atual (D-075).
          { id: 'c-1', name: 'Com CPF', cpf: null, birthDate: '2012-05-20', relationship: 'CHILD' },
          { id: 'c-2', name: 'Sem CPF', cpf: formatted, birthDate: '2012-05-20', relationship: 'FRIEND' },
        ],
      }),
    )
  })

  it('o ADMIN edita o CPF existente e, ao apagar, envia "" para remover', async () => {
    let sent: { companions: { cpf: string | null }[] } | undefined
    const full = fakeCpf()
    const formattedFull = `${full.slice(0, 3)}.${full.slice(3, 6)}.${full.slice(6, 9)}-${full.slice(9)}`
    const visit = fakeVisit({ companions: [fakeCompanion({ id: 'c-1', cpf: formattedFull })] })
    mockVisit('ADMIN', visit, (method, url, body) => {
      if (method === 'PUT' && url === '/api/visits/v-1') {
        sent = body as typeof sent
        return { body: visit }
      }
    })
    const user = userEvent.setup()
    renderApp('/visitas/v-1')

    await user.click(await screen.findByRole('button', { name: 'Editar' }))
    const dialog = await screen.findByRole('dialog', { name: 'Editar visita' })
    const input = within(dialog).getByLabelText('CPF do acompanhante 1 (opcional)')
    expect(input).toHaveValue(formattedFull)
    await user.clear(input)
    await user.click(within(dialog).getByRole('button', { name: 'Salvar' }))

    await waitFor(() => expect(sent?.companions[0].cpf).toBe(''))
  })

  it('W10: envia o id dos existentes, omite o id dos novos e deixa de fora os removidos', async () => {
    let sent: { companions: Record<string, unknown>[] } | undefined
    const visit = fakeVisit({
      notes: 'Nota fictícia',
      hostNotes: 'Anfitrião fictício',
      companions: [
        fakeCompanion({ id: 'c-1', name: 'Primeiro' }),
        fakeCompanion({ id: 'c-2', name: 'Segundo', relationship: 'SPOUSE', birthDate: '1990-01-01' }),
      ],
    })
    mockVisit('PROSPECTOR', visit, (method, url, body) => {
      if (method === 'PUT' && url === '/api/visits/v-1') {
        sent = body as typeof sent
        return { body: { ...visit, companions: [] } }
      }
    })
    const user = userEvent.setup()
    renderApp('/visitas/v-1')

    await user.click(await screen.findByRole('button', { name: 'Editar' }))
    const dialog = await screen.findByRole('dialog', { name: 'Editar visita' })
    await user.click(within(dialog).getByRole('button', { name: 'Remover acompanhante 1' }))
    const name = within(dialog).getByLabelText('Nome do acompanhante 1')
    expect(name).toHaveValue('Segundo')
    await user.clear(name)
    await user.type(name, 'Segundo Renomeado')
    await user.click(within(dialog).getByRole('button', { name: 'Adicionar acompanhante' }))
    await user.type(within(dialog).getByLabelText('Nome do acompanhante 2'), 'Novo')
    await user.selectOptions(within(dialog).getByLabelText('Parentesco do acompanhante 2'), 'Outro')
    fireEvent.change(within(dialog).getByLabelText('Nascimento do acompanhante 2'), { target: { value: '2001-03-03' } })
    await user.click(within(dialog).getByRole('button', { name: 'Salvar' }))

    await waitFor(() => expect(sent).toBeDefined())
    expect(sent).toEqual({
      notes: 'Nota fictícia',
      hostNotes: 'Anfitrião fictício',
      companions: [
        { id: 'c-2', name: 'Segundo Renomeado', cpf: null, birthDate: '1990-01-01', relationship: 'SPOUSE' },
        { name: 'Novo', cpf: null, birthDate: '2001-03-03', relationship: 'OTHER' },
      ],
    })
    expect(sent?.companions[1]).not.toHaveProperty('id')
  })
})

describe('W6 — remarcar', () => {
  function setup(onReschedule: Handler) {
    mockVisit('PROSPECTOR', fakeVisit({ companions: [fakeCompanion()] }), (method, url, body) => {
      if (method === 'POST' && url === '/api/visits/v-1/reschedule') return onReschedule(method, url, body)
      if (method === 'GET' && url === '/api/visits/v-2') {
        return { body: fakeVisit({ id: 'v-2', scheduledDate: '2026-10-15', companions: [fakeCompanion({ id: 'c-9' })] }) }
      }
    })
  }

  async function openDialog() {
    const user = userEvent.setup()
    const view = renderApp('/visitas/v-1')
    await user.click(await screen.findByRole('button', { name: 'Remarcar' }))
    const dialog = await screen.findByRole('dialog', { name: 'Remarcar visita' })
    const setDate = (value: string) =>
      fireEvent.change(within(dialog).getByLabelText('Nova data'), { target: { value } })
    const submit = () => user.click(within(dialog).getByRole('button', { name: 'Remarcar' }))
    return { user, dialog, setDate, submit, ...view }
  }

  it('a nova data precisa ser diferente, não passada e até hoje + 12 meses', async () => {
    const sent: unknown[] = []
    setup((_m, _u, body) => {
      sent.push(body)
      return { body: fakeVisit({ id: 'v-2' }) }
    })
    const { dialog, setDate, submit } = await openDialog()
    const input = within(dialog).getByLabelText('Nova data')
    expect(input).toHaveAttribute('min', '2026-09-24')
    expect(input).toHaveAttribute('max', '2027-09-24')

    setDate('2026-09-30')
    await submit()
    expect(await within(dialog).findByText('Escolha uma data diferente da atual.')).toBeInTheDocument()

    setDate('2026-09-20')
    await submit()
    expect(await within(dialog).findByText('A data não pode ser anterior a hoje.')).toBeInTheDocument()

    setDate('2027-09-25')
    await submit()
    expect(
      await within(dialog).findByText('A data pode ser no máximo 24/09/2027 (12 meses a partir de hoje).'),
    ).toBeInTheDocument()
    expect(sent).toHaveLength(0)
  })

  it('depois do envio, a tela abre a nova visita', async () => {
    let sent: unknown
    setup((_m, _u, body) => {
      sent = body
      return { body: fakeVisit({ id: 'v-2', scheduledDate: '2026-10-15' }) }
    })
    const { setDate, submit, router } = await openDialog()

    setDate('2026-10-15')
    await submit()

    await waitFor(() => expect(router.state.location.pathname).toBe('/visitas/v-2'))
    expect(sent).toEqual({ scheduledDate: '2026-10-15' })
    expect(await screen.findByRole('heading', { name: 'Visita de 15/10/2026' })).toBeInTheDocument()
  })

  it.each([
    [problem(400, 'SCHEDULED_DATE_TOO_FAR'), 'A data da visita pode ser no máximo 12 meses a partir de hoje.'],
    [problem(400, 'SAME_DATE'), 'Escolha uma data diferente da atual.'],
    [problem(409, 'INVALID_VISIT_TRANSITION'), 'Esta ação não é permitida para a situação atual da visita.'],
  ])('%#: erro do backend aparece em português e a tela não muda', async (response, message) => {
    setup(() => response)
    const { dialog, setDate, submit, router } = await openDialog()

    setDate('2026-10-15')
    await submit()

    expect(await within(dialog).findByRole('alert')).toHaveTextContent(message)
    expect(router.state.location.pathname).toBe('/visitas/v-1')
  })
})

describe('W7 — cancelar', () => {
  it('pede confirmação e depois mostra o status atualizado, sem ações', async () => {
    let cancels = 0
    mockVisit('PROSPECTOR', fakeVisit(), (method, url) => {
      if (method === 'PATCH' && url === '/api/visits/v-1/cancel') {
        cancels++
        return { body: fakeVisit({ status: 'CANCELLED', cancelledAt: '2026-09-24T15:00:00Z', canEdit: false }) }
      }
    })
    const user = userEvent.setup()
    renderApp('/visitas/v-1')

    await user.click(await screen.findByRole('button', { name: 'Cancelar visita' }))
    let dialog = await screen.findByRole('dialog', { name: 'Cancelar visita' })
    expect(dialog).toHaveTextContent('o Lead volta para "Contatado"')
    await user.click(within(dialog).getByRole('button', { name: 'Cancelar' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(cancels).toBe(0)

    await user.click(screen.getByRole('button', { name: 'Cancelar visita' }))
    dialog = await screen.findByRole('dialog', { name: 'Cancelar visita' })
    await user.click(within(dialog).getByRole('button', { name: 'Confirmar cancelamento' }))

    expect(await screen.findByText('Cancelada')).toBeInTheDocument()
    expect(cancels).toBe(1)
    for (const action of ACTIONS) {
      expect(screen.queryByRole('button', { name: action })).not.toBeInTheDocument()
    }
  })

  it('cancelamento recusado mostra a mensagem em português', async () => {
    mockVisit('PROSPECTOR', fakeVisit(), (method, url) => {
      if (method === 'PATCH' && url === '/api/visits/v-1/cancel') return problem(409, 'INVALID_VISIT_TRANSITION')
    })
    const user = userEvent.setup()
    renderApp('/visitas/v-1')

    await user.click(await screen.findByRole('button', { name: 'Cancelar visita' }))
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Confirmar cancelamento' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Esta ação não é permitida para a situação atual da visita.')
  })
})
