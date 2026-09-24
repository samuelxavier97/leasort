import { formatDate } from '@/lib/format'
import { RESORT_NAME } from '@/lib/resort'
import type { Invitation } from './api'

/**
 * Imagem de compartilhamento do convite (§13), montada no frontend: nome do Resort, nome do Lead,
 * data, QR, código formatado e a instrução para a portaria. Nada de CPF, telefone, e-mail ou
 * acompanhantes (§15, RN08).
 */
export const SHARE_INSTRUCTION = 'Apresente este código na portaria'

const WIDTH = 1080
const HEIGHT = 1440
const QR_SIZE = 720
const MAX_TEXT_WIDTH = WIDTH - 120

export interface ShareImageContent {
  resortName: string
  title: string
  leadName: string
  date: string
  code: string
  instruction: string
  fileName: string
}

export function shareImageContent(invitation: Invitation): ShareImageContent {
  return {
    resortName: RESORT_NAME,
    title: 'Convite de visita',
    leadName: invitation.lead.name,
    date: `Visita em ${formatDate(invitation.visit.scheduledDate)}`,
    code: invitation.formattedCode,
    instruction: SHARE_INSTRUCTION,
    fileName: `convite-${invitation.formattedCode}.png`,
  }
}

/** Desenha a imagem em canvas e devolve o PNG. */
export async function renderShareImage(content: ShareImageContent, qrPng: Blob): Promise<Blob> {
  const canvas = document.createElement('canvas')
  canvas.width = WIDTH
  canvas.height = HEIGHT
  const context = canvas.getContext('2d')
  if (!context) {
    throw new Error('Canvas indisponível.')
  }
  const qr = await createImageBitmap(qrPng)

  context.fillStyle = '#ffffff'
  context.fillRect(0, 0, WIDTH, HEIGHT)
  context.fillStyle = '#111827'
  context.textAlign = 'center'
  context.textBaseline = 'alphabetic'

  const text = (value: string, y: number, size: number, weight = 'normal', family = 'system-ui, sans-serif') => {
    let fontSize = size
    context.font = `${weight} ${fontSize}px ${family}`
    // Nomes longos: reduz a fonte até caber na largura.
    while (context.measureText(value).width > MAX_TEXT_WIDTH && fontSize > 24) {
      fontSize -= 2
      context.font = `${weight} ${fontSize}px ${family}`
    }
    context.fillText(value, WIDTH / 2, y)
  }

  text(content.resortName, 110, 56, 'bold')
  text(content.title, 180, 40)
  text(content.leadName, 270, 52, 'bold')
  text(content.date, 340, 40)
  context.drawImage(qr, (WIDTH - QR_SIZE) / 2, 390, QR_SIZE, QR_SIZE)
  text(content.code, 1200, 88, 'bold', 'ui-monospace, monospace')
  text(content.instruction, 1300, 40)

  return new Promise((resolve, reject) => {
    canvas.toBlob((blob) => (blob ? resolve(blob) : reject(new Error('Falha ao gerar a imagem.'))), 'image/png')
  })
}

export type DeliveryResult = 'shared' | 'downloaded' | 'cancelled'

/**
 * "Compartilhar" usa a Web Share API quando ela aceita arquivos; senão, baixa (§13). "Baixar" sempre
 * baixa. Cancelar o compartilhamento não é erro.
 */
export async function deliverImage(blob: Blob, content: ShareImageContent, mode: 'share' | 'download'): Promise<DeliveryResult> {
  const file = new File([blob], content.fileName, { type: 'image/png' })
  if (mode === 'share' && typeof navigator.canShare === 'function' && navigator.canShare({ files: [file] })) {
    try {
      await navigator.share({ files: [file], title: content.title })
      return 'shared'
    } catch (error) {
      if (error instanceof DOMException && error.name === 'AbortError') {
        return 'cancelled'
      }
      throw error
    }
  }
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = content.fileName
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
  return 'downloaded'
}
