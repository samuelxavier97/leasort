import { describe, expect, it, vi } from 'vitest'
import { dataUrlBytes, readGrayPng } from '@/test/grayPng'
import { PortariaQrReader, rotateCounterClockwise } from './portariaQrReader'

// Os PNGs do teste com câmera (D-123), gerados como o backend gera: a01–a12 e b01–b08 são os QRs que o
// @zxing/library não lê na orientação normal; c01–c04 são o controle.
const PNGS = import.meta.glob<string>('../../../../docs/qr-teste-camera/*.png', { query: '?inline', import: 'default', eager: true })
const BY_NAME = Object.fromEntries(Object.entries(PNGS).map(([path, dataUrl]) => [path.slice(path.lastIndexOf('/') + 1), dataUrl]))
const FILES = Object.keys(BY_NAME).sort()
const PROBLEMATIC = FILES.filter((name) => !name.startsWith('c'))
const CONTROL = FILES.filter((name) => name.startsWith('c'))

/** O que o loop do scanner faz: um quadro por tentativa, até ler. Devolve em qual quadro leu, ou null. */
async function scan(file: string, frames: number): Promise<{ text: string; frame: number } | null> {
  const { gray, width, height } = await readGrayPng(dataUrlBytes(BY_NAME[file]))
  const reader = new PortariaQrReader()
  for (let frame = 1; frame <= frames; frame++) {
    try {
      return { text: reader.decodeGrayFrame(gray, width, height).getText(), frame }
    } catch {
      // Sem leitura neste quadro: o loop tenta o seguinte.
    }
  }
  return null
}

const codeOf = (file: string) => `RSV:${file.slice(4, -4)}`

describe('Leitor da Portaria: alternância de orientação (D-123)', () => {
  it('os 24 PNGs estão no diretório do teste com câmera', () => {
    expect(PROBLEMATIC).toHaveLength(20)
    expect(CONTROL).toHaveLength(4)
  })

  it.each(CONTROL)('controle %s: lê já no primeiro quadro, na orientação normal', async (file) => {
    expect(await scan(file, 1)).toEqual({ text: codeOf(file), frame: 1 })
  })

  it('problemáticos: nenhum lê só na orientação normal, e os 20 leem no segundo quadro, girado', async () => {
    const normal = (await Promise.all(PROBLEMATIC.map((file) => scan(file, 1)))).filter((result) => result !== null)
    const alternating = await Promise.all(PROBLEMATIC.map(async (file) => ({ file, result: await scan(file, 2) })))
    expect(normal).toEqual([])
    expect(alternating.filter(({ file, result }) => result?.text === codeOf(file) && result.frame === 2)).toHaveLength(20)
  })

  it('alterna a orientação a cada quadro, com uma decodificação por quadro', () => {
    const reader = new PortariaQrReader()
    const decode = vi.spyOn(reader, 'decodeBitmap')
    const blank = new Uint8ClampedArray(30 * 20).fill(255)
    for (let frame = 0; frame < 6; frame++) {
      expect(() => reader.decodeGrayFrame(blank, 30, 20)).toThrow()
    }
    expect(decode.mock.calls.map(([bitmap]) => `${bitmap.getWidth()}×${bitmap.getHeight()}`)).toEqual([
      '30×20',
      '20×30',
      '30×20',
      '20×30',
      '30×20',
      '20×30',
    ])
  })

  it('gira 90° no sentido anti-horário', () => {
    // 3 × 2:  1 2 3      girada (2 × 3):  3 6
    //         4 5 6                       2 5
    //                                     1 4
    const rotated = rotateCounterClockwise(Uint8ClampedArray.from([1, 2, 3, 4, 5, 6]), 3, 2)
    expect([...rotated]).toEqual([3, 6, 2, 5, 1, 4])
  })
})
