import { mkdtemp, rm, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { chromium, devices, type Page } from '@playwright/test'
import { BASE_URL } from './support/api.ts'
import { fakeCpf, OPERATION_TIMEZONE, operationDate } from './support/data.ts'
import { expect, login, test } from './support/fixtures.ts'

const WIDTH = 640
const HEIGHT = 480

/**
 * Converte o PNG do QR num vídeo Y4M em tons de cinza (QR centralizado sobre branco), que o
 * Chromium usa como câmera com --use-file-for-fake-video-capture. O PNG é decodificado num canvas,
 * sem dependência nova.
 */
async function qrVideo(page: Page, png: Buffer): Promise<Buffer> {
  const pixels: number[] = await page.evaluate(
    async ({ dataUrl, width, height }) => {
      const image = new Image()
      image.src = dataUrl
      await image.decode()
      const canvas = document.createElement('canvas')
      canvas.width = width
      canvas.height = height
      const context = canvas.getContext('2d')!
      context.fillStyle = '#fff'
      context.fillRect(0, 0, width, height)
      context.imageSmoothingEnabled = false
      const side = Math.floor(height * 0.8)
      context.drawImage(image, (width - side) / 2, (height - side) / 2, side, side)
      const rgba = context.getImageData(0, 0, width, height).data
      const luma: number[] = []
      for (let i = 0; i < rgba.length; i += 4) luma.push(Math.round(0.299 * rgba[i] + 0.587 * rgba[i + 1] + 0.114 * rgba[i + 2]))
      return luma
    },
    { dataUrl: `data:image/png;base64,${png.toString('base64')}`, width: WIDTH, height: HEIGHT },
  )
  const y = Buffer.from(pixels.map((value) => 16 + Math.round((value * 219) / 255)))
  const chroma = Buffer.alloc((WIDTH / 2) * (HEIGHT / 2), 128)
  const frame = Buffer.concat([Buffer.from('FRAME\n'), y, chroma, chroma])
  const header = Buffer.from(`YUV4MPEG2 W${WIDTH} H${HEIGHT} F30:1 Ip A1:1 C420jpeg\n`)
  return Buffer.concat([header, ...Array.from({ length: 30 }, () => frame)])
}

/** E4: a Portaria lê o QR real do convite pela câmera (simulada) do Chromium, sem digitar nada. */
test('E4: a câmera lê o QR do convite e a Portaria vê o acesso liberado', async ({ world, page }) => {
  const prospector = await world.createUser('PROSPECTOR')
  const gate = await world.createUser('GATE')
  const leadName = `Lead E2E ${world.suffix}`
  const lead = await world.createLead({
    name: leadName,
    cpf: fakeCpf(),
    phone: '(11) 90000-0002',
    birthDate: '1990-11-02',
    prospectorId: prospector.prospectorId!,
  })

  // Agendamento pela API: o foco aqui é a leitura pela câmera, não o agendamento (coberto no E1).
  const prospectorApi = await world.apiAs(prospector)
  const visit = await prospectorApi.json<{ invitation: { id: string } }>('POST', '/api/visits', {
    leadId: lead.id,
    scheduledDate: operationDate(),
    notes: null,
    hostNotes: null,
    companions: [],
  })
  const png = await (await prospectorApi.get(`/api/invitations/${visit.invitation.id}/qr-code`)).body()

  // Fora de test-results: o Chromium não abre o vídeo num caminho com acentos (o do teste tem "câmera").
  const videoDir = await mkdtemp(join(tmpdir(), 'e2e-qr-'))
  const video = join(videoDir, 'qr.y4m')
  await writeFile(video, await qrVideo(page, png))

  // Um Chromium só da Portaria, com o vídeo como câmera e a permissão concedida.
  const cameraBrowser = await chromium.launch({
    args: ['--use-fake-device-for-media-stream', `--use-file-for-fake-video-capture=${video}`],
    ...(process.env.E2E_CHROMIUM_PATH ? { executablePath: process.env.E2E_CHROMIUM_PATH } : {}),
  })
  try {
    const context = await cameraBrowser.newContext({
      ...devices['Pixel 7'],
      baseURL: BASE_URL,
      locale: 'pt-BR',
      timezoneId: OPERATION_TIMEZONE,
      permissions: ['camera'],
    })
    const gatePage = await context.newPage()
    await login(gatePage, gate.email, gate.password)
    await expect(gatePage).toHaveURL('/portaria')
    await gatePage.getByRole('button', { name: 'ESCANEAR QR CODE' }).click()
    await expect(gatePage.getByRole('heading', { name: 'ACESSO LIBERADO' })).toBeVisible({ timeout: 15_000 })
    await expect(gatePage.getByText(leadName, { exact: true })).toBeVisible()
    await expect(gatePage.getByText('Visita sem acompanhantes.')).toBeVisible()
    // A câmera foi parada depois da leitura (§16.5).
    await expect(gatePage.getByLabel('Imagem da câmera')).toHaveCount(0)
  } finally {
    await cameraBrowser.close()
    await rm(videoDir, { recursive: true, force: true })
  }
})
