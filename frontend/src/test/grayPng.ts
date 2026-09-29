/**
 * Lê um PNG em tons de cinza (tipo de cor 0, 1 a 8 bits, sem entrelaçamento), como os do QR do backend,
 * e devolve a luminância de cada pixel de 0 a 255. Só para testes: o jsdom não tem canvas, e isto evita uma dependência nova.
 */
export async function readGrayPng(bytes: Uint8Array): Promise<{ gray: Uint8ClampedArray; width: number; height: number }> {
  const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength)
  let width = 0
  let height = 0
  let bitDepth = 0
  const idat: Uint8Array[] = []
  for (let offset = 8; offset < bytes.length; ) {
    const length = view.getUint32(offset)
    const type = String.fromCharCode(...bytes.subarray(offset + 4, offset + 8))
    const data = bytes.subarray(offset + 8, offset + 8 + length)
    if (type === 'IHDR') {
      width = view.getUint32(offset + 8)
      height = view.getUint32(offset + 12)
      bitDepth = data[8]
      if (data[9] !== 0 || data[12] !== 0 || bitDepth > 8) throw new Error('Só PNG em tons de cinza, até 8 bits, sem entrelaçamento.')
    } else if (type === 'IDAT') {
      idat.push(data)
    }
    offset += 12 + length
  }

  const raw = await inflate(idat)
  const stride = Math.ceil((width * bitDepth) / 8)
  const lines = new Uint8Array(stride * height)
  for (let y = 0; y < height; y++) {
    const filter = raw[y * (stride + 1)]
    for (let i = 0; i < stride; i++) {
      const value = raw[y * (stride + 1) + 1 + i]
      const left = i > 0 ? lines[y * stride + i - 1] : 0
      const up = y > 0 ? lines[(y - 1) * stride + i] : 0
      const upLeft = i > 0 && y > 0 ? lines[(y - 1) * stride + i - 1] : 0
      lines[y * stride + i] = (value + predictor(filter, left, up, upLeft)) & 0xff
    }
  }

  const max = (1 << bitDepth) - 1
  const gray = new Uint8ClampedArray(width * height)
  for (let y = 0; y < height; y++) {
    for (let x = 0; x < width; x++) {
      const bit = x * bitDepth
      const sample = (lines[y * stride + (bit >> 3)] >> (8 - bitDepth - (bit & 7))) & max
      gray[y * width + x] = Math.round((sample * 255) / max)
    }
  }
  return { gray, width, height }
}

function predictor(filter: number, left: number, up: number, upLeft: number): number {
  switch (filter) {
    case 0:
      return 0
    case 1:
      return left
    case 2:
      return up
    case 3:
      return (left + up) >> 1
    case 4: {
      const p = left + up - upLeft
      const [pa, pb, pc] = [Math.abs(p - left), Math.abs(p - up), Math.abs(p - upLeft)]
      return pa <= pb && pa <= pc ? left : pb <= pc ? up : upLeft
    }
    default:
      throw new Error(`Filtro PNG desconhecido: ${filter}`)
  }
}

/** Descompacta o zlib dos blocos IDAT com a API de streams do navegador (também presente no Node). */
async function inflate(chunks: Uint8Array[]): Promise<Uint8Array> {
  const compressed = new ReadableStream<BufferSource>({
    start(controller) {
      for (const chunk of chunks) controller.enqueue(chunk.slice())
      controller.close()
    },
  })
  const stream = compressed.pipeThrough(new DecompressionStream('deflate'))
  return new Uint8Array(await new Response(stream).arrayBuffer())
}

/** Os bytes de um PNG embutido como data URL (`import ... ?inline`). */
export function dataUrlBytes(dataUrl: string): Uint8Array {
  return Uint8Array.from(atob(dataUrl.slice(dataUrl.indexOf(',') + 1)), (char) => char.charCodeAt(0))
}
