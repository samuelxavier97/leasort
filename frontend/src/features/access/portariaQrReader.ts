import { BrowserQRCodeReader, HTMLCanvasElementLuminanceSource } from '@zxing/browser'
import { BinaryBitmap, HybridBinarizer, RGBLuminanceSource, type Result } from '@zxing/library'

/** Uma tentativa a cada 500 ms, o padrão do @zxing/browser: a alternância não aumenta esse ritmo (D-123). */
const DELAY_BETWEEN_ATTEMPTS_MS = 500

/** Gira 90° no sentido anti-horário uma imagem em tons de cinza: `width × height` vira `height × width`. */
export function rotateCounterClockwise(gray: Uint8ClampedArray, width: number, height: number): Uint8ClampedArray {
  const rotated = new Uint8ClampedArray(width * height)
  for (let y = 0; y < height; y++) {
    for (let x = 0; x < width; x++) {
      rotated[(width - 1 - x) * height + y] = gray[y * width + x]
    }
  }
  return rotated
}

/**
 * Leitor do scanner da Portaria (D-123): alterna a orientação a cada quadro, normal e girada 90° no
 * sentido anti-horário, porque o detector do @zxing/library não acha os padrões de localização de
 * parte dos QRs corretos numa das orientações. Continua uma decodificação por quadro.
 *
 * Depende de o loop do `BrowserCodeReader` (`scan`) chamar `decodeFromCanvas` a cada quadro; por isso as
 * versões do @zxing ficam fixas no package.json, e o E4b reprova se uma atualização mudar o loop.
 */
export class PortariaQrReader extends BrowserQRCodeReader {
  private frame = 0

  constructor() {
    super(undefined, { delayBetweenScanAttempts: DELAY_BETWEEN_ATTEMPTS_MS, delayBetweenScanSuccess: DELAY_BETWEEN_ATTEMPTS_MS })
  }

  override decodeFromCanvas(canvas: HTMLCanvasElement): Result {
    // Os tons de cinza com a mesma conta da biblioteca.
    const gray = new HTMLCanvasElementLuminanceSource(canvas).getMatrix()
    return this.decodeGrayFrame(gray, canvas.width, canvas.height)
  }

  /** Decodifica um quadro: os ímpares na orientação normal, os pares girados. */
  decodeGrayFrame(gray: Uint8ClampedArray, width: number, height: number): Result {
    const rotated = this.frame++ % 2 === 1
    const source = rotated
      ? new RGBLuminanceSource(rotateCounterClockwise(gray, width, height), height, width)
      : new RGBLuminanceSource(gray, width, height)
    return this.decodeBitmap(new BinaryBitmap(new HybridBinarizer(source)))
  }
}
