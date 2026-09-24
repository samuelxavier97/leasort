import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { fakeCpf } from '@/test/fakeCpf'
import { fakeLead, page } from '@/test/leadFixtures'
import { me, mockFetch, png, problem, renderApp, type Handler } from '@/test/utils'
import { fakeInvitation } from '@/test/invitationFixtures'
import { fakeVisit } from '@/test/visitFixtures'

// 12h de 24/09/2026 em São Paulo: hoje = 24/09/2026 e o limite é 24/09/2027 (D-074, D-080).
const NOW = new Date('2026-09-24T15:00:00Z')

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] })
  vi.setSystemTime(NOW)
})

afterEach(() => {
  vi.useRealTimers()
})

function mockApi(onCreate: (body: unknown) => ReturnType<Handler>) {
  return mockFetch((method, url, body) => {
    if (url === '/api/auth/me') return { body: me('PROSPECTOR') }
    if (method === 'GET' && url === '/api/leads/lead-1') return { body: fakeLead({ status: 'CONTACTED' }) }
    if (method === 'GET' && url.startsWith('/api/visits?')) return { body: page([]) }
    if (method === 'POST' && url === '/api/visits') return onCreate(body)
    if (method === 'GET' && url === '/api/invitations/i-new') return { body: fakeInvitation({ id: 'i-new' }) }
    if (method === 'GET' && url === '/api/invitations/i-new/qr-code') return png()
  })
}

async function openDialog() {
  const user = userEvent.setup()
  const view = renderApp('/leads/lead-1')
  await user.click(await screen.findByRole('button', { name: 'Agendar visita' }))
  const dialog = await screen.findByRole('dialog', { name: 'Agendar visita' })
  return { user, dialog, ...view }
}

function setDate(label: string | RegExp, value: string) {
  fireEvent.change(screen.getByLabelText(label), { target: { value } })
}

describe('W1 — formulário de agendamento (§16.3)', () => {
  it('as seções opcionais começam recolhidas e o fluxo mínimo é data e confirmar', async () => {
    let sent: unknown
    mockApi((body) => {
      sent = body
      return { status: 201, body: fakeVisit({ id: 'v-new', invitation: { id: 'i-new', status: 'ACTIVE' } }) }
    })
    const { user, dialog, router } = await openDialog()

    const companions = within(dialog).getByRole('button', { name: 'Acompanhantes (opcional)' })
    const hostNotes = within(dialog).getByRole('button', { name: 'Observações para o anfitrião (opcional)' })
    expect(companions).toHaveAttribute('aria-expanded', 'false')
    expect(hostNotes).toHaveAttribute('aria-expanded', 'false')
    expect(within(dialog).queryByRole('button', { name: 'Adicionar acompanhante' })).not.toBeInTheDocument()
    expect(within(dialog).queryByRole('textbox', { name: 'Observações para o anfitrião' })).not.toBeInTheDocument()

    setDate('Data da visita', '2026-09-30')
    await user.click(within(dialog).getByRole('button', { name: 'Confirmar agendamento' }))

    await waitFor(() =>
      expect(sent).toEqual({ leadId: 'lead-1', scheduledDate: '2026-09-30', notes: null, hostNotes: null, companions: [] }),
    )
    // C8 (§16.3): confirmar → convite gerado → tela do convite.
    await waitFor(() => expect(router.state.location.pathname).toBe('/convites/i-new'))
    expect(await screen.findByRole('heading', { name: 'Convite' })).toBeInTheDocument()
  })

  it('a data é obrigatória, não pode ser passada e vai até hoje + 12 meses', async () => {
    const sent: unknown[] = []
    mockApi((body) => {
      sent.push(body)
      return { status: 201, body: fakeVisit({ id: 'v-new' }) }
    })
    const { user, dialog } = await openDialog()
    const input = within(dialog).getByLabelText('Data da visita')
    const confirm = within(dialog).getByRole('button', { name: 'Confirmar agendamento' })

    expect(input).toHaveAttribute('min', '2026-09-24')
    expect(input).toHaveAttribute('max', '2027-09-24')

    await user.click(confirm)
    expect(await within(dialog).findByText('Informe a data da visita.')).toBeInTheDocument()

    setDate('Data da visita', '2026-09-23')
    await user.click(confirm)
    expect(await within(dialog).findByText('A data não pode ser anterior a hoje.')).toBeInTheDocument()

    setDate('Data da visita', '2027-09-25')
    await user.click(confirm)
    expect(
      await within(dialog).findByText('A data pode ser no máximo 24/09/2027 (12 meses a partir de hoje).'),
    ).toBeInTheDocument()
    expect(sent).toHaveLength(0)

    // Limite exato: hoje + 12 meses é aceito.
    setDate('Data da visita', '2027-09-24')
    await user.click(confirm)
    await waitFor(() => expect(sent).toHaveLength(1))
    expect(sent[0]).toMatchObject({ scheduledDate: '2027-09-24' })
  })

  it('adiciona e remove acompanhantes, valida os obrigatórios e envia o corpo esperado', async () => {
    let sent: unknown
    mockApi((body) => {
      sent = body
      return { status: 201, body: fakeVisit({ id: 'v-new' }) }
    })
    const cpf = fakeCpf()
    const { user, dialog } = await openDialog()
    setDate('Data da visita', '2026-10-05')

    await user.click(within(dialog).getByRole('button', { name: 'Acompanhantes (opcional)' }))
    await user.click(within(dialog).getByRole('button', { name: 'Adicionar acompanhante' }))
    await user.click(within(dialog).getByRole('button', { name: 'Adicionar acompanhante' }))
    await user.click(within(dialog).getByRole('button', { name: 'Adicionar acompanhante' }))
    expect(within(dialog).getAllByRole('group')).toHaveLength(3)
    await user.click(within(dialog).getByRole('button', { name: 'Remover acompanhante 3' }))
    expect(within(dialog).getAllByRole('group')).toHaveLength(2)

    // Parentesco com rótulos em português.
    const relationship = within(dialog).getByLabelText('Parentesco do acompanhante 1')
    expect(within(relationship).getAllByRole('option').map((option) => option.textContent)).toEqual([
      'Selecione',
      'Cônjuge',
      'Filho(a)',
      'Pai',
      'Mãe',
      'Irmão(ã)',
      'Avô/Avó',
      'Neto(a)',
      'Amigo(a)',
      'Outro',
    ])

    // Obrigatórios vazios e CPF inválido: nada é enviado.
    await user.type(within(dialog).getByLabelText('CPF do acompanhante 2 (opcional)'), '11111111111')
    await user.click(within(dialog).getByRole('button', { name: 'Confirmar agendamento' }))
    expect(await within(dialog).findAllByText('Informe o nome.')).toHaveLength(2)
    expect(within(dialog).getAllByText('Informe o parentesco.')).toHaveLength(2)
    expect(within(dialog).getAllByText('Informe a data de nascimento.')).toHaveLength(2)
    expect(within(dialog).getByText('CPF inválido.')).toBeInTheDocument()
    expect(sent).toBeUndefined()

    await user.type(within(dialog).getByLabelText('Nome do acompanhante 1'), 'Cônjuge Fictícia')
    await user.selectOptions(relationship, 'Cônjuge')
    setDate('Nascimento do acompanhante 1', '1988-02-10')
    await user.type(within(dialog).getByLabelText('Nome do acompanhante 2'), 'Filho Fictício')
    await user.selectOptions(within(dialog).getByLabelText('Parentesco do acompanhante 2'), 'Filho(a)')
    setDate('Nascimento do acompanhante 2', '2015-07-01')
    const cpfInput = within(dialog).getByLabelText('CPF do acompanhante 2 (opcional)')
    await user.clear(cpfInput)
    await user.type(cpfInput, cpf)
    await user.click(within(dialog).getByRole('button', { name: 'Observações para o anfitrião (opcional)' }))
    await user.type(within(dialog).getByRole('textbox', { name: 'Observações para o anfitrião' }), 'Prefere a área de lazer')
    await user.click(within(dialog).getByRole('button', { name: 'Confirmar agendamento' }))

    const formatted = `${cpf.slice(0, 3)}.${cpf.slice(3, 6)}.${cpf.slice(6, 9)}-${cpf.slice(9)}`
    await waitFor(() =>
      expect(sent).toEqual({
        leadId: 'lead-1',
        scheduledDate: '2026-10-05',
        notes: null,
        hostNotes: 'Prefere a área de lazer',
        companions: [
          { name: 'Cônjuge Fictícia', cpf: null, birthDate: '1988-02-10', relationship: 'SPOUSE' },
          { name: 'Filho Fictício', cpf: formatted, birthDate: '2015-07-01', relationship: 'CHILD' },
        ],
      }),
    )
  })

  it('um erro em seção recolhida abre a seção', async () => {
    mockApi(() => ({ status: 201, body: fakeVisit({ id: 'v-new' }) }))
    const { user, dialog } = await openDialog()
    setDate('Data da visita', '2026-10-05')
    const toggle = within(dialog).getByRole('button', { name: 'Acompanhantes (opcional)' })
    await user.click(toggle)
    await user.click(within(dialog).getByRole('button', { name: 'Adicionar acompanhante' }))
    await user.click(within(dialog).getByRole('button', { name: 'Acompanhantes (1)' }))
    expect(within(dialog).getByRole('button', { name: 'Acompanhantes (1)' })).toHaveAttribute('aria-expanded', 'false')

    await user.click(within(dialog).getByRole('button', { name: 'Confirmar agendamento' }))

    expect(await within(dialog).findByText('Informe o nome.')).toBeVisible()
    expect(within(dialog).getByRole('button', { name: 'Acompanhantes (1)' })).toHaveAttribute('aria-expanded', 'true')
  })
})

describe('W2 — erros do backend em português', () => {
  it.each([
    [problem(400, 'TOO_MANY_COMPANIONS', 'No máximo 6 acompanhantes por visita.'), 'No máximo 6 acompanhantes por visita.'],
    [problem(409, 'LEAD_INACTIVE'), 'Este Lead foi descartado. Reative-o antes de agendar uma visita.'],
    [problem(409, 'VISIT_ALREADY_SCHEDULED'), 'Este Lead já tem uma visita agendada.'],
    [problem(400, 'SCHEDULED_DATE_TOO_FAR'), 'A data da visita pode ser no máximo 12 meses a partir de hoje.'],
    [problem(400, 'SCHEDULED_DATE_IN_PAST'), 'A data da visita não pode ser anterior a hoje.'],
  ])('%#: mostra a mensagem do código', async (response, message) => {
    mockApi(() => response)
    const { user, dialog, router } = await openDialog()
    setDate('Data da visita', '2026-09-30')

    await user.click(within(dialog).getByRole('button', { name: 'Confirmar agendamento' }))

    expect(await within(dialog).findByRole('alert')).toHaveTextContent(message)
    expect(router.state.location.pathname).toBe('/leads/lead-1')
  })
})
