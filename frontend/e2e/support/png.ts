import type { Page } from '@playwright/test'
// O pacote é CommonJS para o Node: os nomes vêm do objeto exportado.
import zxing from '@zxing/library'

const { BinaryBitmap, DecodeHintType, HybridBinarizer, QRCodeReader, RGBLuminanceSource } = zxing

const PNG_SIGNATURE = [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]

export interface PngContent {
  width: number
  height: number
  /** Texto do QR Code encontrado na imagem. */
  qrText: string
}

/**
 * Confere que os bytes são um PNG válido (assinatura e decodificação pelo navegador) e lê o QR Code
 * da imagem. A decodificação usa uma página em branco, sem a CSP do site, para não gerar violação.
 */
export async function readPng(blankPage: Page, bytes: Buffer): Promise<PngContent> {
  if (!PNG_SIGNATURE.every((byte, index) => bytes[index] === byte)) {
    throw new Error('O arquivo não começa com a assinatura PNG.')
  }
  const { width, height, luminance } = await blankPage.evaluate(async (base64) => {
    const image = new Image()
    image.src = `data:image/png;base64,${base64}`
    await image.decode()
    const canvas = document.createElement('canvas')
    canvas.width = image.naturalWidth
    canvas.height = image.naturalHeight
    const context = canvas.getContext('2d')!
    context.drawImage(image, 0, 0)
    const rgba = context.getImageData(0, 0, canvas.width, canvas.height).data
    const gray: number[] = []
    for (let i = 0; i < rgba.length; i += 4) gray.push(Math.round(0.299 * rgba[i] + 0.587 * rgba[i + 1] + 0.114 * rgba[i + 2]))
    return { width: canvas.width, height: canvas.height, luminance: gray }
  }, bytes.toString('base64'))

  const source = new RGBLuminanceSource(Uint8ClampedArray.from(luminance), width, height)
  const hints = new Map([[DecodeHintType.TRY_HARDER, true]])
  const qrText = new QRCodeReader().decode(new BinaryBitmap(new HybridBinarizer(source)), hints).getText()
  return { width, height, qrText }
}
