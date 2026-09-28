import { formatDate } from '@/lib/format'
import { brandName, currentBrand } from '@/lib/brand'
import type { Invitation } from './api'

/**
 * Imagem de compartilhamento do convite (§13), montada no frontend: a identidade do cliente (D-117: o
 * logotipo, se houver; senão, RESORT_NAME; senão, Resortric) sobre uma faixa na cor principal, o nome
 * do Lead, a data, o QR, o código formatado e a instrução para a portaria. Nada de CPF, telefone,
 * e-mail ou acompanhantes (§15, RN08).
 */
export const SHARE_INSTRUCTION = 'Apresente este código na portaria'

const WIDTH = 1080
const HEIGHT = 1440
const BAND_HEIGHT = 24
/** Caixa do logotipo: o logotipo é contido nela, sem distorcer, qualquer que seja a proporção. */
export const LOGO_BOX = { x: 180, y: 64, width: 720, height: 160 }
const QR_SIZE = 680
/** Área do QR na imagem (usada também pelo E2E para conferir a nitidez). */
export const QR_BOX = { x: (WIDTH - QR_SIZE) / 2, y: 470, width: QR_SIZE, height: QR_SIZE }
const MAX_TEXT_WIDTH = WIDTH - 120

export interface ShareImageContent {
  /** Nome em texto da identidade, usado quando não há logotipo (ou ele não carrega). */
  resortName: string
  logoUrl: string | null
  color: string
  title: string
  leadName: string
  date: string
  code: string
  instruction: string
  fileName: string
}

export function shareImageContent(invitation: Invitation): ShareImageContent {
  const brand = currentBrand()
  return {
    resortName: brandName(brand),
    logoUrl: brand.logoUrl,
    color: brand.color,
    title: 'Convite de visita',
    leadName: invitation.lead.name,
    date: `Visita em ${formatDate(invitation.visit.scheduledDate)}`,
    code: invitation.formattedCode,
    instruction: SHARE_INSTRUCTION,
    fileName: `convite-${invitation.formattedCode}.png`,
  }
}

/** Maior retângulo com a proporção da imagem dentro da caixa, centralizado. */
export function containRect(width: number, height: number, box: typeof LOGO_BOX) {
  const scale = Math.min(box.width / width, box.height / height)
  const drawWidth = width * scale
  const drawHeight = height * scale
  return { x: box.x + (box.width - drawWidth) / 2, y: box.y + (box.height - drawHeight) / 2, width: drawWidth, height: drawHeight }
}

/** O logotipo da própria origem (/brand/logo.png), por um <img> (D-119); falha vira null. */
async function loadLogo(url: string): Promise<HTMLImageElement | null> {
  const image = new Image()
  image.src = url
  try {
    await image.decode()
    return image.naturalWidth > 0 && image.naturalHeight > 0 ? image : null
  } catch {
    return null
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
  const [qr, logo] = await Promise.all([createImageBitmap(qrPng), content.logoUrl ? loadLogo(content.logoUrl) : null])

  context.fillStyle = '#ffffff'
  context.fillRect(0, 0, WIDTH, HEIGHT)
  // Faixa decorativa na cor principal do cliente (D-117); nenhum texto fica sobre ela.
  context.fillStyle = content.color
  context.fillRect(0, 0, WIDTH, BAND_HEIGHT)
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

  if (logo) {
    const rect = containRect(logo.naturalWidth, logo.naturalHeight, LOGO_BOX)
    context.drawImage(logo, rect.x, rect.y, rect.width, rect.height)
  } else {
    text(content.resortName, 164, 56, 'bold')
  }
  text(content.title, 290, 40)
  text(content.leadName, 370, 52, 'bold')
  text(content.date, 430, 40)
  // O QR vem da API com 512 px (módulos de 20,48 px) e é ampliado: sem suavização, cada módulo fica só
  // preto ou branco, com bordas nítidas, qualquer que seja o filtro de reamostragem do navegador. Com
  // suavização, as bordas interpoladas mudavam o tamanho de módulo que o leitor estima.
  context.imageSmoothingEnabled = false
  context.drawImage(qr, QR_BOX.x, QR_BOX.y, QR_BOX.width, QR_BOX.height)
  context.imageSmoothingEnabled = true
  text(content.code, 1250, 88, 'bold', 'ui-monospace, monospace')
  text(content.instruction, 1335, 40)

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
