import type { Page } from '@playwright/test'
// O pacote é CommonJS para o Node: os nomes vêm do objeto exportado.
import zxing from '@zxing/library'
// Só para o diagnóstico de uma falha: os candidatos a padrão de localização do QR (classe interna).
import finderModule from '@zxing/library/cjs/core/qrcode/detector/FinderPatternFinder.js'

const { BinaryBitmap, DecodeHintType, HybridBinarizer, QRCodeReader, RGBLuminanceSource } = zxing
const FinderPatternFinder = finderModule.default

const PNG_SIGNATURE = [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]

export interface PngContent {
  width: number
  height: number
  /** Texto do QR Code encontrado na imagem. */
  qrText: string
  /** Luminância (0 a 255) de cada pixel, linha a linha. */
  luminance: number[]
}

/** Pixels com tom intermediário (nem quase preto nem quase branco) dentro de uma área da imagem. */
export function midTones(png: PngContent, box: { x: number; y: number; width: number; height: number }): number {
  let count = 0
  for (let y = box.y; y < box.y + box.height; y++) {
    for (let x = box.x; x < box.x + box.width; x++) {
      const value = png.luminance[y * png.width + x]
      if (value > 40 && value < 215) count++
    }
  }
  return count
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
  try {
    const qrText = new QRCodeReader().decode(new BinaryBitmap(new HybridBinarizer(source)), hints).getText()
    return { width, height, qrText, luminance }
  } catch (error) {
    throw new Error(`QR não lido na imagem inteira (${String(error)}). ${diagnose(source, hints)}`)
  }
}

/**
 * Diagnóstico de uma leitura que falhou: os candidatos a padrão de localização que o detector achou
 * na imagem (posição, tamanho do módulo e quantas vezes foi confirmado) e se só a metade de baixo da
 * imagem, onde fica o QR, é lida. Separa um QR danificado de uma interferência de fora dele.
 */
function diagnose(source: InstanceType<typeof RGBLuminanceSource>, hints: Map<zxing.DecodeHintType, unknown>): string {
  // getPossibleCenters é protegido na tipagem, mas público no JavaScript.
  const finder = new FinderPatternFinder(new HybridBinarizer(source).getBlackMatrix() as never, undefined as never) as unknown as {
    find(hints: Map<zxing.DecodeHintType, unknown>): unknown
    getPossibleCenters(): { getX(): number; getY(): number; getEstimatedModuleSize(): number; getCount(): number }[]
  }
  try {
    finder.find(hints)
  } catch {
    // Os candidatos ficam guardados mesmo quando a busca falha.
  }
  const centers = finder
    .getPossibleCenters()
    .map((p) =>
      `(${Math.round(p.getX())}, ${Math.round(p.getY())}) módulo ${p.getEstimatedModuleSize().toFixed(1)} vistas ${p.getCount()}`,
    )
  let lowerHalf: string
  try {
    const top = Math.floor(source.getHeight() / 3)
    const crop = source.crop(0, top, source.getWidth(), source.getHeight() - top)
    lowerHalf = `lido: ${new QRCodeReader().decode(new BinaryBitmap(new HybridBinarizer(crop)), hints).getText()}`
  } catch (error) {
    lowerHalf = `também falhou (${String(error)})`
  }
  return `Candidatos: ${centers.join('; ') || 'nenhum'}. Só a parte de baixo (a partir de 1/3 da altura): ${lowerHalf}.`
}
