import { act, render, screen, waitFor, within } from '@testing-library/react'
import { StrictMode } from 'react'
import userEvent from '@testing-library/user-event'
import { toast } from 'sonner'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { fakeAuthorized } from '@/test/accessFixtures'
import { me, mockFetch, problem, renderApp, type MockResponse } from '@/test/utils'
import type { AccessResponse, DenialReason } from './api'
import { QrScanner } from './QrScanner'

type DecodeCallback = (result: { getText: () => string } | undefined, error: unknown, controls: { stop: () => void }) => void

// P5: @zxing/browser simulado. `start` registra cada abertura da câmera; `stop`, cada parada.
const camera = vi.hoisted(() => ({
  start: vi.fn(),
  stop: vi.fn(),
  callback: undefined as DecodeCallback | undefined,
  failWith: undefined as unknown,
  videos: [] as unknown[],
}))

vi.mock('@zxing/browser', () => ({
  BrowserQRCodeReader: class {
    decodeFromConstraints(constraints: MediaStreamConstraints, video: unknown, callback: DecodeCallback) {
      camera.start(constraints)
      camera.videos.push(video)
      if (camera.failWith) return Promise.reject(camera.failWith)
      camera.callback = callback
      return Promise.resolve({ stop: camera.stop })
    }
  },
}))

function readQr(text: string) {
  act(() => camera.callback?.({ getText: () => text }, undefined, { stop: camera.stop }))
}

function setSecureContext(secure: boolean) {
  Object.defineProperty(window, 'isSecureContext', { value: secure, configurable: true })
}

interface Calls {
  validate: unknown[]
  register: unknown[]
}

/** Portaria com respostas por chamada; a última resposta se repete. */
function renderGate(validate: MockResponse[] | MockResponse, register: MockResponse[] | MockResponse = { body: {} }) {
  const calls: Calls = { validate: [], register: [] }
  const queue = (responses: MockResponse[] | MockResponse, count: number) =>
    Array.isArray(responses) ? responses[Math.min(count, responses.length - 1)] : responses
  mockFetch((method, url, body) => {
    if (url === '/api/auth/me') return { body: me('GATE') }
    if (method === 'POST' && url === '/api/access/validate') {
      calls.validate.push(body)
      return queue(validate, calls.validate.length - 1)
    }
    if (method === 'POST' && url === '/api/access/register') {
      calls.register.push(body)
      return queue(register, calls.register.length - 1)
    }
    if (url === '/api/access/recent') return { body: [] }
  })
  return { calls, ...renderApp('/portaria') }
}

const denied = (denialReason: DenialReason, scheduledDate?: string): MockResponse => ({
  body: { result: 'DENIED', denialReason, scheduledDate } satisfies AccessResponse,
})

async function typeCode(user: ReturnType<typeof userEvent.setup>, text: string) {
  await user.click(await screen.findByRole('button', { name: 'DIGITAR CÓDIGO' }))
  await user.type(screen.getByLabelText('Código do convite'), text)
}

async function validateTyped(user: ReturnType<typeof userEvent.setup>, text = 'ABCDEFGHJK') {
  await typeCode(user, text)
  await user.click(screen.getByRole('button', { name: 'VALIDAR' }))
}

beforeEach(() => {
  camera.start.mockClear()
  camera.stop.mockClear()
  camera.callback = undefined
  camera.failWith = undefined
  camera.videos = []
  setSecureContext(true)
  Object.defineProperty(navigator, 'mediaDevices', { value: { getUserMedia: vi.fn() }, configurable: true })
})

afterEach(() => {
  vi.restoreAllMocks()
})

describe('Portaria — digitação (P2)', () => {
  it('exibe XXXXX-XXXXX com as trocas e envia os 10 caracteres sem hífen', async () => {
    const user = userEvent.setup()
    const { calls } = renderGate(denied('INVALID_CODE'))

    await typeCode(user, 'abcde-oil9u.zzzz')
    expect(screen.getByLabelText('Código do convite')).toHaveValue('ABCDE-0119Z')

    await user.click(screen.getByRole('button', { name: 'VALIDAR' }))
    await screen.findByText('ACESSO NEGADO')
    expect(calls.validate).toEqual([{ code: 'ABCDE0119Z' }])
  })

  it('só valida com 10 caracteres', async () => {
    const user = userEvent.setup()
    renderGate(denied('INVALID_CODE'))

    await typeCode(user, 'ABCDE1234')
    expect(screen.getByRole('button', { name: 'VALIDAR' })).toBeDisabled()
    await user.type(screen.getByLabelText('Código do convite'), '5')
    expect(screen.getByRole('button', { name: 'VALIDAR' })).toBeEnabled()
  })
})

describe('Portaria — liberado (P3)', () => {
  it('mostra nome, data, Prospector e acompanhantes todos marcados', async () => {
    const user = userEvent.setup()
    renderGate({ body: fakeAuthorized() })

    await validateTyped(user)

    expect(await screen.findByRole('heading', { name: 'ACESSO LIBERADO' })).toBeInTheDocument()
    expect(screen.getByText('Lead Fictício')).toBeInTheDocument()
    expect(screen.getByText('24/09/2026')).toBeInTheDocument()
    expect(screen.getByText('Prospector Fictício')).toBeInTheDocument()
    const companions = screen.getAllByRole('checkbox')
    expect(companions).toHaveLength(2)
    companions.forEach((box) => expect(box).toBeChecked())
    expect(screen.getByRole('checkbox', { name: /Acompanhante Um.*Cônjuge/ })).toBeInTheDocument()
    expect(screen.getByRole('checkbox', { name: /Acompanhante Dois.*Filho\(a\)/ })).toBeInTheDocument()
  })

  it('desmarcar um acompanhante tira o id do registro e, depois de confirmar, volta ao scanner', async () => {
    const user = userEvent.setup()
    const toastSuccess = vi.spyOn(toast, 'success')
    const { calls } = renderGate(
      { body: fakeAuthorized() },
      { body: { result: 'AUTHORIZED', invitationId: 'i-1', leadName: 'Lead Fictício', accessRecordId: 'a-1', entryAt: '2026-09-24T13:00:00Z' } },
    )

    await validateTyped(user)
    await user.click(await screen.findByRole('checkbox', { name: /Acompanhante Um/ }))
    await user.click(screen.getByRole('button', { name: 'CONFIRMAR ENTRADA' }))

    await waitFor(() => expect(calls.register).toEqual([{ invitationId: 'i-1', presentCompanionIds: ['c-2'] }]))
    expect(toastSuccess).toHaveBeenCalledWith('Entrada de Lead Fictício registrada.')
    expect(await screen.findByLabelText('Imagem da câmera')).toBeInTheDocument()
    await waitFor(() => expect(camera.start).toHaveBeenCalledTimes(1))
  })

  it('visita sem acompanhantes confirma com lista vazia', async () => {
    const user = userEvent.setup()
    const { calls } = renderGate({ body: fakeAuthorized({ companions: [] }) }, { body: { result: 'AUTHORIZED', leadName: 'Lead Fictício' } })

    await validateTyped(user)
    expect(await screen.findByText('Visita sem acompanhantes.')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'CONFIRMAR ENTRADA' }))

    await waitFor(() => expect(calls.register).toEqual([{ invitationId: 'i-1', presentCompanionIds: [] }]))
  })

  it('erro no registro fica na tela de liberado, com a mensagem', async () => {
    const user = userEvent.setup()
    renderGate({ body: fakeAuthorized() }, problem(400, 'COMPANION_NOT_FOUND'))

    await validateTyped(user)
    await user.click(await screen.findByRole('button', { name: 'CONFIRMAR ENTRADA' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Um dos acompanhantes não pertence mais a esta visita.')
    expect(screen.getByRole('heading', { name: 'ACESSO LIBERADO' })).toBeInTheDocument()
  })
})

describe('Portaria — negado (P4)', () => {
  const reasons: { reason: DenialReason; date?: string; title: string; message: string }[] = [
    { reason: 'INVALID_CODE', title: 'Código inválido', message: 'Nenhum convite encontrado com este código. Confira o código e tente novamente.' },
    { reason: 'CANCELLED', title: 'Convite cancelado', message: 'Este convite foi cancelado e não dá mais acesso.' },
    { reason: 'ALREADY_USED', title: 'Convite já utilizado', message: 'Este convite já foi utilizado. A entrada já está registrada.' },
    { reason: 'EXPIRED', date: '2026-09-20', title: 'Convite expirado', message: 'Este convite expirou. A visita era em 20/09/2026.' },
    { reason: 'WRONG_DATE', date: '2026-09-30', title: 'Fora da data', message: 'Este convite não é para hoje. A visita está marcada para 30/09/2026.' },
  ]

  it.each(reasons)('$reason mostra "$title" e o motivo em português', async ({ reason, date, title, message }) => {
    const user = userEvent.setup()
    renderGate(denied(reason, date))

    await validateTyped(user)

    expect(await screen.findByRole('heading', { name: 'ACESSO NEGADO' })).toBeInTheDocument()
    expect(screen.getByText(title)).toBeInTheDocument()
    expect(screen.getByText(message)).toBeInTheDocument()
  })

  it('"Nova validação" volta ao scanner', async () => {
    const user = userEvent.setup()
    renderGate(denied('CANCELLED'))

    await validateTyped(user)
    await user.click(await screen.findByRole('button', { name: 'NOVA VALIDAÇÃO' }))

    expect(await screen.findByLabelText('Imagem da câmera')).toBeInTheDocument()
    await waitFor(() => expect(camera.start).toHaveBeenCalledTimes(1))
  })

  it('negativa na confirmação (clique duplo) mostra a tela de negado', async () => {
    const user = userEvent.setup()
    renderGate({ body: fakeAuthorized() }, denied('ALREADY_USED'))

    await validateTyped(user)
    await user.click(await screen.findByRole('button', { name: 'CONFIRMAR ENTRADA' }))

    expect(await screen.findByRole('heading', { name: 'ACESSO NEGADO' })).toBeInTheDocument()
    expect(screen.getByText('Convite já utilizado')).toBeInTheDocument()
  })
})

describe('Portaria — scanner (P5)', () => {
  it('a leitura valida o texto como foi lido e para a câmera', async () => {
    const user = userEvent.setup()
    const { calls } = renderGate(denied('INVALID_CODE'))

    await user.click(await screen.findByRole('button', { name: 'ESCANEAR QR CODE' }))
    await waitFor(() => expect(camera.callback).toBeDefined())
    expect(camera.start).toHaveBeenCalledWith({ video: { facingMode: 'environment' } })
    readQr('RSV:abcde-fghjk')
    // Parada logo após a leitura, antes da resposta da validação.
    expect(camera.stop).toHaveBeenCalledTimes(1)

    await screen.findByRole('heading', { name: 'ACESSO NEGADO' })
    expect(calls.validate).toEqual([{ code: 'RSV:abcde-fghjk' }])
    expect(camera.stop).toHaveBeenCalled()
  })

  it('leituras seguidas do mesmo QR validam uma vez só', async () => {
    const user = userEvent.setup()
    const { calls } = renderGate(denied('INVALID_CODE'))

    await user.click(await screen.findByRole('button', { name: 'ESCANEAR QR CODE' }))
    await waitFor(() => expect(camera.callback).toBeDefined())
    readQr('RSV:ABCDEFGHJK')
    readQr('RSV:ABCDEFGHJK')

    await screen.findByRole('heading', { name: 'ACESSO NEGADO' })
    expect(calls.validate).toHaveLength(1)
  })

  it('sair da tela para a câmera', async () => {
    const user = userEvent.setup()
    const { router } = renderGate(denied('INVALID_CODE'))

    await user.click(await screen.findByRole('button', { name: 'ESCANEAR QR CODE' }))
    await waitFor(() => expect(camera.callback).toBeDefined())
    expect(camera.stop).not.toHaveBeenCalled()

    await act(() => router.navigate('/acessos'))

    await waitFor(() => expect(camera.stop).toHaveBeenCalled())
  })

  it('no StrictMode, cada abertura da câmera usa o seu vídeo e só o último fica na tela', async () => {
    // O zxing limpa o vídeo que recebeu ao parar; com um vídeo compartilhado, a parada da primeira
    // montagem apagava a imagem da segunda (achado na verificação manual no Chromium).
    render(
      <StrictMode>
        <QrScanner onRead={() => undefined} onProblem={() => undefined} />
      </StrictMode>,
    )

    await waitFor(() => expect(camera.videos).toHaveLength(2))
    expect(camera.videos[0]).not.toBe(camera.videos[1])
    const onScreen = screen.getAllByLabelText('Imagem da câmera')
    expect(onScreen).toEqual([camera.videos[1]])
  })

  it('sem contexto seguro, avisa, não abre a câmera e deixa a digitação', async () => {
    setSecureContext(false)
    const user = userEvent.setup()
    renderGate(denied('INVALID_CODE'))

    await user.click(await screen.findByRole('button', { name: 'ESCANEAR QR CODE' }))

    expect(await screen.findByRole('status')).toHaveTextContent('A câmera só funciona em conexão segura (HTTPS).')
    expect(camera.start).not.toHaveBeenCalled()
    expect(screen.getByLabelText('Código do convite')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'ESCANEAR QR CODE' })).not.toBeInTheDocument()
  })

  it('sem permissão, avisa e deixa a digitação', async () => {
    camera.failWith = new DOMException('negado', 'NotAllowedError')
    const user = userEvent.setup()
    renderGate(denied('INVALID_CODE'))

    await user.click(await screen.findByRole('button', { name: 'ESCANEAR QR CODE' }))

    expect(await screen.findByRole('status')).toHaveTextContent('Sem permissão para usar a câmera.')
    expect(screen.getByLabelText('Código do convite')).toBeInTheDocument()
  })

  it('sem câmera no aparelho, avisa e deixa a digitação', async () => {
    camera.failWith = new DOMException('nenhuma', 'NotFoundError')
    const user = userEvent.setup()
    renderGate(denied('INVALID_CODE'))

    await user.click(await screen.findByRole('button', { name: 'ESCANEAR QR CODE' }))

    expect(await screen.findByRole('status')).toHaveTextContent('Nenhuma câmera disponível neste aparelho.')
  })

  it('com a câmera indisponível, "Nova validação" e a confirmação voltam à digitação com o campo limpo', async () => {
    camera.failWith = new DOMException('negado', 'NotAllowedError')
    const user = userEvent.setup()
    renderGate([denied('CANCELLED'), { body: fakeAuthorized() }], { body: { result: 'AUTHORIZED', leadName: 'Lead Fictício' } })

    await user.click(await screen.findByRole('button', { name: 'ESCANEAR QR CODE' }))
    await user.type(await screen.findByLabelText('Código do convite'), 'ABCDEFGHJK')
    await user.click(screen.getByRole('button', { name: 'VALIDAR' }))
    await user.click(await screen.findByRole('button', { name: 'NOVA VALIDAÇÃO' }))

    expect(await screen.findByLabelText('Código do convite')).toHaveValue('')
    expect(screen.queryByLabelText('Imagem da câmera')).not.toBeInTheDocument()

    await user.type(screen.getByLabelText('Código do convite'), 'ABCDEFGHJK')
    await user.click(screen.getByRole('button', { name: 'VALIDAR' }))
    await user.click(await screen.findByRole('button', { name: 'CONFIRMAR ENTRADA' }))

    expect(await screen.findByLabelText('Código do convite')).toHaveValue('')
    // A câmera não é pedida de novo a cada validação.
    expect(camera.start).toHaveBeenCalledTimes(1)
  })
})

describe('Portaria — limite de validações (P6)', () => {
  it('429 TOO_MANY_VALIDATIONS aparece em português e mantém o código digitado', async () => {
    const user = userEvent.setup()
    renderGate(problem(429, 'TOO_MANY_VALIDATIONS', 'Too many'))

    await validateTyped(user)

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Muitas validações em pouco tempo. Aguarde um minuto e tente novamente.',
    )
    expect(screen.getByLabelText('Código do convite')).toHaveValue('ABCDE-FGHJK')
  })

  it('429 depois de uma leitura não reabre a câmera sozinho', async () => {
    const user = userEvent.setup()
    renderGate(problem(429, 'TOO_MANY_VALIDATIONS'))

    await user.click(await screen.findByRole('button', { name: 'ESCANEAR QR CODE' }))
    await waitFor(() => expect(camera.callback).toBeDefined())
    readQr('RSV:ABCDEFGHJK')

    const alert = await screen.findByRole('alert')
    expect(within(alert).getByText(/Muitas validações/)).toBeInTheDocument()
    expect(screen.queryByLabelText('Imagem da câmera')).not.toBeInTheDocument()
    expect(camera.start).toHaveBeenCalledTimes(1)
  })
})
