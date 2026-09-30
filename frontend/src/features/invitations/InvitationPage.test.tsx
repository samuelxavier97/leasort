import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { toast } from 'sonner'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { Role } from '@/features/auth/types'
import { PRODUCT_NAME } from '@/lib/brand'
import { fakeInvitation } from '@/test/invitationFixtures'
import { page } from '@/test/leadFixtures'
import { me, mockFetch, png, problem, renderApp, setBrand, type Handler } from '@/test/utils'
import type { Invitation } from './api'
import { containRect, LOGO_BOX, modulesFromPixels, SHARE_INSTRUCTION } from './shareImage'

/** Canvas falso: registra todo texto desenhado, para conferir o conteúdo exato da imagem. */
let drawnTexts: string[] = []
let drawImage = vi.fn()

/**
 * Pixels RGBA de um QR sintético (sem margem na matriz) em `size` px. `zxing` imita o PNG do backend
 * (QRCodeWriter): módulos de pixels inteiros para 4 módulos de margem e a sobra dividida nas bordas;
 * sem ele, módulos fracionários (tamanho / (n + 4)).
 */
function qrPixels(matrix: boolean[][], size: number, zxing = true): Uint8ClampedArray {
  const n = matrix.length
  const module = zxing ? Math.floor(size / (n + 4)) : size / (n + 4)
  const padding = zxing ? Math.floor((size - n * module) / 2) : 2 * module
  const data = new Uint8ClampedArray(size * size * 4).fill(255)
  for (let y = 0; y < size; y++) {
    for (let x = 0; x < size; x++) {
      const row = Math.floor((y - padding) / module)
      const column = Math.floor((x - padding) / module)
      if (row >= 0 && row < n && column >= 0 && column < n && matrix[row][column]) data.fill(0, (y * size + x) * 4, (y * size + x) * 4 + 3)
    }
  }
  return data
}

/** QR versão 1 (21 × 21): os três padrões de localização e o resto preenchido de forma fixa. */
function fakeQrMatrix(): boolean[][] {
  const finder = (row: number, column: number, top: number, left: number) => {
    const r = row - top
    const c = column - left
    if (r < 0 || r > 6 || c < 0 || c > 6) return null
    return r === 0 || r === 6 || c === 0 || c === 6 || (r >= 2 && r <= 4 && c >= 2 && c <= 4)
  }
  return Array.from({ length: 21 }, (_, row) =>
    Array.from({ length: 21 }, (_, column) => {
      const pattern = finder(row, column, 0, 0) ?? finder(row, column, 0, 14) ?? finder(row, column, 14, 0)
      if (pattern !== null) return pattern
      if ((row <= 7 && column <= 7) || (row <= 7 && column >= 13) || (row >= 13 && column <= 7)) return false
      return (row * 7 + column * 3) % 5 < 2
    }),
  )
}

beforeEach(() => {
  drawnTexts = []
  drawImage = vi.fn()
  const QR_PNG_SIZE = 512
  const context = {
    fillRect: vi.fn(),
    drawImage,
    getImageData: () => ({ data: qrPixels(fakeQrMatrix(), QR_PNG_SIZE) }),
    measureText: () => ({ width: 100 }),
    fillText: (text: string) => drawnTexts.push(text),
  }
  vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue(context as never)
  vi.spyOn(HTMLCanvasElement.prototype, 'toBlob').mockImplementation(function (callback: BlobCallback) {
    callback(new Blob(['imagem'], { type: 'image/png' }))
  })
  vi.stubGlobal('createImageBitmap', vi.fn(async () => ({ width: QR_PNG_SIZE, height: QR_PNG_SIZE })))
})

afterEach(() => {
  vi.restoreAllMocks()
  delete (navigator as { canShare?: unknown }).canShare
  delete (navigator as { share?: unknown }).share
})

function mockInvitation(role: Role, invitation: Invitation, extra?: Handler) {
  return mockFetch((method, url, body) => {
    if (url === '/api/auth/me') return { body: me(role) }
    const handled = extra?.(method, url, body)
    if (handled) return handled
    if (method === 'GET' && url === `/api/invitations/${invitation.id}`) return { body: invitation }
    if (method === 'GET' && url === `/api/invitations/${invitation.id}/qr-code`) return png()
    if (method === 'GET' && url.startsWith('/api/visits?')) return { body: page([]) }
  })
}

const ACTIONS = ['Compartilhar', 'Baixar', 'Reemitir código', 'Cancelar visita']

describe('C1 — tela do convite (§16.4)', () => {
  it('mostra Lead, data, Prospector, acompanhantes, status, QR e o código formatado', async () => {
    const invitation = fakeInvitation()
    mockInvitation('PROSPECTOR', invitation)
    renderApp('/convites/i-1')

    expect(await screen.findByRole('heading', { name: 'Convite' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Lead Fictício' })).toHaveAttribute('href', '/leads/lead-1')
    expect(screen.getByText('30/09/2026')).toBeInTheDocument()
    expect(screen.getByText('Prospector Fictício')).toBeInTheDocument()
    expect(screen.getByText('2')).toBeInTheDocument()
    expect(screen.getAllByText('Ativo').length).toBeGreaterThan(0)
    expect(await screen.findByRole('img', { name: 'QR Code do convite' })).toHaveAttribute('src', 'blob:teste')
    expect(screen.getByTestId('invitation-code')).toHaveTextContent(invitation.formattedCode)
    expect(invitation.formattedCode).toMatch(/^[0-9A-HJKMNP-TV-Z]{5}-[0-9A-HJKMNP-TV-Z]{5}$/)
  })
})

describe('C2 — botões por estado', () => {
  it('convite ACTIVE com escrita: os quatro botões', async () => {
    mockInvitation('PROSPECTOR', fakeInvitation())
    renderApp('/convites/i-1')

    await screen.findByRole('img', { name: 'QR Code do convite' })
    for (const action of ACTIONS) {
      expect(screen.getByRole('button', { name: action })).toBeInTheDocument()
    }
  })

  it('quem só lê: compartilha e baixa, mas não reemite nem cancela, e vê o Lead sem link', async () => {
    mockInvitation(
      'PROSPECTOR',
      fakeInvitation({
        canReissue: false,
        visit: { id: 'v-1', scheduledDate: '2026-09-30', status: 'SCHEDULED', companionsCount: 0, canEdit: false },
        lead: { id: 'lead-1', name: 'Lead Fictício', accessible: false },
      }),
    )
    renderApp('/convites/i-1')

    expect(await screen.findByRole('button', { name: 'Compartilhar' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Baixar' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Reemitir código' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Cancelar visita' })).not.toBeInTheDocument()
    expect(screen.getByText('Lead Fictício')).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Lead Fictício' })).not.toBeInTheDocument()
  })

  it.each(['CANCELLED', 'USED', 'EXPIRED'] as const)('convite %s: sem QR, sem compartilhar nem baixar', async (status) => {
    const fetchMock = mockInvitation(
      'PROSPECTOR',
      fakeInvitation({
        status,
        canReissue: false,
        visit: { id: 'v-1', scheduledDate: '2026-09-30', status: 'CANCELLED', companionsCount: 0, canEdit: false },
      }),
    )
    renderApp('/convites/i-1')

    expect(await screen.findByText('Este convite não é mais válido e não pode ser compartilhado.')).toBeInTheDocument()
    for (const action of ACTIONS) {
      expect(screen.queryByRole('button', { name: action })).not.toBeInTheDocument()
    }
    expect(screen.queryByRole('img', { name: 'QR Code do convite' })).not.toBeInTheDocument()
    expect(screen.queryByTestId('invitation-code')).not.toBeInTheDocument()
    expect(fetchMock.mock.calls.map(([url]) => String(url))).not.toContain('/api/invitations/i-1/qr-code')
    expect(screen.getByRole('link', { name: 'Ver visita' })).toHaveAttribute('href', '/visitas/v-1')
  })
})

describe('C2 — convite reemitido', () => {
  it('o convite antigo de uma visita ainda agendada não oferece nenhuma ação', async () => {
    mockInvitation(
      'PROSPECTOR',
      fakeInvitation({
        status: 'CANCELLED',
        canReissue: false,
        visit: { id: 'v-1', scheduledDate: '2026-09-30', status: 'SCHEDULED', companionsCount: 0, canEdit: true },
      }),
    )
    renderApp('/convites/i-1')

    expect(await screen.findByText('Este convite não é mais válido e não pode ser compartilhado.')).toBeInTheDocument()
    for (const action of ACTIONS) {
      expect(screen.queryByRole('button', { name: action })).not.toBeInTheDocument()
    }
    expect(screen.getByRole('link', { name: 'Ver visita' })).toHaveAttribute('href', '/visitas/v-1')
  })
})

describe('C3 — reemitir', () => {
  it('pede confirmação e depois abre o novo convite', async () => {
    let reissues = 0
    const created = fakeInvitation({ id: 'i-2' })
    mockInvitation('PROSPECTOR', fakeInvitation(), (method, url) => {
      if (method === 'POST' && url === '/api/invitations/i-1/reissue') {
        reissues++
        return { body: created }
      }
      if (method === 'GET' && url === '/api/invitations/i-2') return { body: created }
      if (method === 'GET' && url === '/api/invitations/i-2/qr-code') return png()
    })
    const user = userEvent.setup()
    const { router } = renderApp('/convites/i-1')

    await user.click(await screen.findByRole('button', { name: 'Reemitir código' }))
    const dialog = await screen.findByRole('dialog', { name: 'Reemitir código' })
    expect(dialog).toHaveTextContent('O código atual deixará de valer')
    await user.click(within(dialog).getByRole('button', { name: 'Cancelar' }))
    expect(reissues).toBe(0)

    await user.click(screen.getByRole('button', { name: 'Reemitir código' }))
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Reemitir' }))

    await waitFor(() => expect(router.state.location.pathname).toBe('/convites/i-2'))
    expect(reissues).toBe(1)
    // A URL muda antes de a página renderizar o convite novo, e o código antigo segue na tela nesse intervalo:
    // espera pelo código novo, não só pela existência do elemento.
    await waitFor(() => expect(screen.getByTestId('invitation-code')).toHaveTextContent(created.formattedCode))
  })

  it('409 INVITATION_NOT_ACTIVE aparece em português', async () => {
    mockInvitation('PROSPECTOR', fakeInvitation(), (method, url) => {
      if (method === 'POST' && url === '/api/invitations/i-1/reissue') return problem(409, 'INVITATION_NOT_ACTIVE')
    })
    const user = userEvent.setup()
    renderApp('/convites/i-1')

    await user.click(await screen.findByRole('button', { name: 'Reemitir código' }))
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Reemitir' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Este convite não está mais ativo.')
  })
})

describe('C4 — cancelar visita pelo convite', () => {
  it('pede confirmação e depois mostra o convite cancelado, sem ações', async () => {
    let cancelled = false
    mockFetch((method, url) => {
      if (url === '/api/auth/me') return { body: me('PROSPECTOR') }
      if (method === 'PATCH' && url === '/api/visits/v-1/cancel') {
        cancelled = true
        return { body: {} }
      }
      if (method === 'GET' && url === '/api/invitations/i-1') {
        return {
          body: cancelled
            ? fakeInvitation({
                status: 'CANCELLED',
                canReissue: false,
                visit: { id: 'v-1', scheduledDate: '2026-09-30', status: 'CANCELLED', companionsCount: 2, canEdit: false },
              })
            : fakeInvitation(),
        }
      }
      if (method === 'GET' && url === '/api/invitations/i-1/qr-code') return png()
    })
    const user = userEvent.setup()
    renderApp('/convites/i-1')

    await user.click(await screen.findByRole('button', { name: 'Cancelar visita' }))
    const dialog = await screen.findByRole('dialog', { name: 'Cancelar visita' })
    expect(dialog).toHaveTextContent('o Lead volta para "Contatado"')
    await user.click(within(dialog).getByRole('button', { name: 'Confirmar cancelamento' }))

    expect(await screen.findByText('Este convite não é mais válido e não pode ser compartilhado.')).toBeInTheDocument()
    expect(screen.getAllByText('Cancelado').length).toBeGreaterThan(0)
    for (const action of ACTIONS) {
      expect(screen.queryByRole('button', { name: action })).not.toBeInTheDocument()
    }
  })
})

describe('C5 a C7 — imagem de compartilhamento', () => {
  const invitation = fakeInvitation({ lead: { id: 'lead-1', name: 'Maria Fictícia', accessible: true } })

  it('C5: a imagem traz só a identidade, título, nome, data, código e instrução', async () => {
    const downloads: string[] = []
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
      downloads.push(this.download)
    })
    mockInvitation('PROSPECTOR', invitation)
    const user = userEvent.setup()
    renderApp('/convites/i-1')

    await screen.findByRole('img', { name: 'QR Code do convite' })
    await user.click(screen.getByRole('button', { name: 'Baixar' }))

    await waitFor(() => expect(downloads).toEqual([`convite-${invitation.formattedCode}.png`]))
    expect(drawnTexts).toEqual([
      // Sem RESORT_NAME nem logotipo, a identidade é a do Resortric (D-117).
      PRODUCT_NAME,
      'Convite de visita',
      'Maria Fictícia',
      'Visita em 30/09/2026',
      invitation.formattedCode,
      'Apresente este código na portaria',
    ])
    expect(SHARE_INSTRUCTION).toBe('Apresente este código na portaria')
    // Nada de CPF, telefone, e-mail, acompanhantes ou Prospector na imagem.
    const everything = drawnTexts.join('\n')
    expect(everything).not.toMatch(/\d{3}\.?\d{3}\.?\d{3}-?\d{2}|\*\*\*/)
    expect(everything).not.toMatch(/@|\(\d{2}\)|\d{4,5}-\d{4}/)
    expect(everything).not.toMatch(/Acompanhante|Prospector Fictício/)
  })

  it('C5b: numa instalação de demonstração, a imagem do convite não traz a etiqueta (D-125)', async () => {
    setBrand({ demo: 'true' })
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined)
    mockInvitation('PROSPECTOR', invitation)
    const user = userEvent.setup()
    renderApp('/convites/i-1')

    await screen.findByRole('img', { name: 'QR Code do convite' })
    await user.click(screen.getByRole('button', { name: 'Baixar' }))
    await waitFor(() => expect(drawnTexts).toContain('Apresente este código na portaria'))
    expect(drawnTexts).toEqual([
      PRODUCT_NAME,
      'Convite de visita',
      'Maria Fictícia',
      'Visita em 30/09/2026',
      invitation.formattedCode,
      'Apresente este código na portaria',
    ])
    expect(drawnTexts.join('\n')).not.toMatch(/demonstra/i)
  })

  it('C6: com a Web Share API, compartilha o PNG; cancelar não mostra erro', async () => {
    const shared: ShareData[] = []
    let cancel = false
    Object.defineProperty(navigator, 'canShare', { configurable: true, value: () => true })
    Object.defineProperty(navigator, 'share', {
      configurable: true,
      value: async (data: ShareData) => {
        if (cancel) throw new DOMException('cancelado', 'AbortError')
        shared.push(data)
      },
    })
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click')
    const toastError = vi.spyOn(toast, 'error')
    mockInvitation('PROSPECTOR', invitation)
    const user = userEvent.setup()
    renderApp('/convites/i-1')

    await screen.findByRole('img', { name: 'QR Code do convite' })
    await user.click(screen.getByRole('button', { name: 'Compartilhar' }))

    await waitFor(() => expect(shared).toHaveLength(1))
    const file = shared[0].files?.[0]
    expect(file?.type).toBe('image/png')
    expect(file?.name).toBe(`convite-${invitation.formattedCode}.png`)
    expect(click).not.toHaveBeenCalled()

    cancel = true
    await user.click(screen.getByRole('button', { name: 'Compartilhar' }))
    await waitFor(() => expect(screen.getByRole('button', { name: 'Compartilhar' })).toBeEnabled())
    expect(toastError).not.toHaveBeenCalled()
    expect(click).not.toHaveBeenCalled()
  })

  it('C6: uma falha real no compartilhamento avisa o usuário', async () => {
    Object.defineProperty(navigator, 'canShare', { configurable: true, value: () => true })
    Object.defineProperty(navigator, 'share', {
      configurable: true,
      value: async () => {
        throw new DOMException('negado', 'NotAllowedError')
      },
    })
    const toastError = vi.spyOn(toast, 'error')
    mockInvitation('PROSPECTOR', invitation)
    const user = userEvent.setup()
    renderApp('/convites/i-1')

    await screen.findByRole('img', { name: 'QR Code do convite' })
    await user.click(screen.getByRole('button', { name: 'Compartilhar' }))

    await waitFor(() => expect(toastError).toHaveBeenCalledWith('Não foi possível gerar a imagem do convite.'))
  })

  it('C7: sem a Web Share API, Compartilhar baixa; Baixar sempre baixa', async () => {
    const downloads: string[] = []
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
      downloads.push(this.download)
    })
    mockInvitation('PROSPECTOR', invitation)
    const user = userEvent.setup()
    renderApp('/convites/i-1')

    await screen.findByRole('img', { name: 'QR Code do convite' })
    await user.click(screen.getByRole('button', { name: 'Compartilhar' }))
    await waitFor(() => expect(downloads).toHaveLength(1))

    Object.defineProperty(navigator, 'canShare', { configurable: true, value: () => true })
    Object.defineProperty(navigator, 'share', { configurable: true, value: vi.fn() })
    await user.click(screen.getByRole('button', { name: 'Baixar' }))
    await waitFor(() => expect(downloads).toHaveLength(2))
    expect(navigator.share).not.toHaveBeenCalled()
  })
})

describe('C12 — erros em português', () => {
  it.each([
    [problem(404, 'INVITATION_NOT_FOUND'), 'Convite não encontrado.'],
    [problem(409, 'INVITATION_NOT_ACTIVE'), 'Este convite não está mais ativo.'],
    [problem(409, 'INVITATION_CODE_CONFLICT'), 'Não foi possível gerar o código do convite. Tente novamente.'],
  ])('%#', async (response, message) => {
    mockInvitation('PROSPECTOR', fakeInvitation(), (method, url) => {
      if (method === 'POST' && url === '/api/invitations/i-1/reissue') return response
    })
    const user = userEvent.setup()
    renderApp('/convites/i-1')

    await user.click(await screen.findByRole('button', { name: 'Reemitir código' }))
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Reemitir' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(message)
  })

  it('convite inexistente ou fora da carteira mostra "Convite não encontrado."', async () => {
    mockFetch((_method, url) => {
      if (url === '/api/auth/me') return { body: me('PROSPECTOR') }
      if (url === '/api/invitations/i-9') return problem(404, 'INVITATION_NOT_FOUND')
    })
    renderApp('/convites/i-9')

    expect(await screen.findByText('Convite não encontrado.')).toBeInTheDocument()
  })
})

describe('C12 — identidade na imagem do convite (D-117)', () => {
  const invitation = fakeInvitation({ lead: { id: 'lead-1', name: 'Maria Fictícia', accessible: true } })
  const RESORT = 'Resort Fictício das Águas'

  /** Logotipo que "carrega" no jsdom com as dimensões dadas; decode falha se `fails`. */
  function logoLoads(width: number, height: number, fails = false) {
    // O jsdom não implementa decode(); os navegadores, sim.
    Object.defineProperty(HTMLImageElement.prototype, 'decode', {
      configurable: true,
      value: () => (fails ? Promise.reject(new Error('404')) : Promise.resolve()),
    })
    vi.spyOn(HTMLImageElement.prototype, 'naturalWidth', 'get').mockReturnValue(width)
    vi.spyOn(HTMLImageElement.prototype, 'naturalHeight', 'get').mockReturnValue(height)
  }

  async function download() {
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
    mockInvitation('PROSPECTOR', invitation)
    const user = userEvent.setup()
    renderApp('/convites/i-1')
    await screen.findByRole('img', { name: 'QR Code do convite' })
    await user.click(screen.getByRole('button', { name: 'Baixar' }))
    await waitFor(() => expect(drawnTexts).toContain(SHARE_INSTRUCTION))
  }

  afterEach(() => {
    delete (HTMLImageElement.prototype as { decode?: unknown }).decode
  })

  const logoCall = () => drawImage.mock.calls.find(([image]) => image instanceof HTMLImageElement)

  it('com logotipo: desenha o /brand/logo.png contido na caixa e não escreve o nome', async () => {
    setBrand({ name: RESORT, logo: '/brand/logo.png' })
    logoLoads(480, 120)
    await download()

    const [image, x, y, width, height] = logoCall()!
    expect((image as HTMLImageElement).getAttribute('src')).toBe('/brand/logo.png')
    // 480 × 120 escalado para 640 × 160 (altura da caixa), centralizado nos 720 px.
    expect([x, y, width, height]).toEqual([220, 64, 640, 160])
    expect(drawnTexts).not.toContain(RESORT)
    expect(drawnTexts[0]).toBe('Convite de visita')
  })

  it('logotipo que não carrega: cai no nome em texto', async () => {
    setBrand({ name: RESORT, logo: '/brand/logo.png' })
    logoLoads(480, 120, true)
    await download()

    expect(logoCall()).toBeUndefined()
    expect(drawnTexts[0]).toBe(RESORT)
  })

  it('só com RESORT_NAME: o nome em texto, nunca "Resortric"', async () => {
    setBrand({ name: RESORT })
    await download()

    expect(logoCall()).toBeUndefined()
    expect(drawnTexts[0]).toBe(RESORT)
    expect(drawnTexts).not.toContain(PRODUCT_NAME)
  })

  it('logotipo muito largo ou muito alto cabe na caixa sem distorcer', () => {
    const wide = containRect(2000, 100, LOGO_BOX)
    expect(wide).toEqual({ x: 180, y: 126, width: 720, height: 36 })
    const tall = containRect(100, 800, LOGO_BOX)
    expect(tall).toEqual({ x: 530, y: 64, width: 20, height: 160 })
    for (const rect of [wide, tall]) {
      expect(rect.x).toBeGreaterThanOrEqual(LOGO_BOX.x)
      expect(rect.y + rect.height).toBeLessThanOrEqual(LOGO_BOX.y + LOGO_BOX.height)
    }
  })
})

describe('C13 — QR redesenhado módulo a módulo', () => {
  it('lê a grade exata do PNG da API (512 px: módulos de 20 px e 46 px de margem), sem a margem', () => {
    const matrix = fakeQrMatrix()
    expect(modulesFromPixels(qrPixels(matrix, 512), 512)).toEqual(matrix)
  })

  it('também com módulos fracionários (20,48 px) e outros tamanhos, sem supor a margem', () => {
    const matrix = fakeQrMatrix()
    expect(modulesFromPixels(qrPixels(matrix, 512, false), 512)).toEqual(matrix)
    expect(modulesFromPixels(qrPixels(matrix, 250), 250)).toEqual(matrix)
    expect(modulesFromPixels(qrPixels(matrix, 777, false), 777)).toEqual(matrix)
  })

  it('PNG sem padrão de localização é erro, não um QR vazio', () => {
    expect(() => modulesFromPixels(new Uint8ClampedArray(64 * 64 * 4).fill(255), 64)).toThrow('QR sem padrão de localização.')
  })
})

