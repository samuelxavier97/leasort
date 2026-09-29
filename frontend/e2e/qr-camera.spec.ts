import { mkdtemp, readFile, rm, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { chromium, devices, type Page, type TestInfo } from '@playwright/test'
import { BASE_URL, IGNORE_HTTPS_ERRORS } from './support/api.ts'
import { fakeCpf, OPERATION_TIMEZONE, operationDate } from './support/data.ts'
import { expect, login, test, watchCsp, type Person, type World } from './support/fixtures.ts'

const WIDTH = 640
const HEIGHT = 480
// PNGs do teste com câmera (D-123), gerados como o backend gera. O Playwright roda a partir de frontend/.
const QR_TEST_DIR = join(process.cwd(), '..', 'docs', 'qr-teste-camera')

/**
 * Converte o PNG do QR num vídeo Y4M em tons de cinza (QR centralizado sobre branco, com o lado igual a
 * `fraction` da altura do quadro), que o Chromium usa como câmera com --use-file-for-fake-video-capture.
 * O PNG é decodificado num canvas, sem dependência nova.
 */
async function qrVideo(page: Page, png: Buffer, fraction: number): Promise<Buffer> {
  const pixels: number[] = await page.evaluate(
    async ({ dataUrl, width, height, fraction }) => {
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
      const side = Math.floor(height * fraction)
      context.drawImage(image, (width - side) / 2, (height - side) / 2, side, side)
      const rgba = context.getImageData(0, 0, width, height).data
      const luma: number[] = []
      for (let i = 0; i < rgba.length; i += 4) luma.push(Math.round(0.299 * rgba[i] + 0.587 * rgba[i + 1] + 0.114 * rgba[i + 2]))
      return luma
    },
    { dataUrl: `data:image/png;base64,${png.toString('base64')}`, width: WIDTH, height: HEIGHT, fraction },
  )
  const y = Buffer.from(pixels.map((value) => 16 + Math.round((value * 219) / 255)))
  const chroma = Buffer.alloc((WIDTH / 2) * (HEIGHT / 2), 128)
  const frame = Buffer.concat([Buffer.from('FRAME\n'), y, chroma, chroma])
  const header = Buffer.from(`YUV4MPEG2 W${WIDTH} H${HEIGHT} F30:1 Ip A1:1 C420jpeg\n`)
  return Buffer.concat([header, ...Array.from({ length: 30 }, () => frame)])
}

/** E4: a Portaria lê o QR real do convite pela câmera (simulada) do Chromium, sem digitar nada. */
test('E4: a câmera lê o QR do convite e a Portaria vê o acesso liberado', async ({ world, page }, testInfo) => {
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

  await scanWithCamera(world, page, png, testInfo, gate, 'E4', 0.8, async (gatePage) => {
    await expect(gatePage.getByRole('heading', { name: 'ACESSO LIBERADO' })).toBeVisible({ timeout: 15_000 })
    await expect(gatePage.getByText(leadName, { exact: true })).toBeVisible()
    await expect(gatePage.getByText('Visita sem acompanhantes.')).toBeVisible()
  })
})

/**
 * E4b: pela câmera (simulada), o scanner lê um QR que o @zxing/library só acha girado (a01, o único dos
 * 20 que o giro horário não resgata) e um de controle (c01), gerados como o backend gera (D-123). O QR
 * ocupa 70% da altura: nessa escala o a01 só é lido girado; a 80%, a do E4, não é lido em nenhuma das
 * duas orientações. Os códigos não existem no banco: "Código inválido" é a prova de que a câmera leu.
 * É o único teste com câmera que depende da alternância: se uma atualização do @zxing/browser deixar de
 * chamar `decodeFromCanvas` no loop, o E4 continua passando e só o E4b reprova.
 */
test('E4b: a câmera lê os QRs que só o giro resgata, e os de controle', async ({ world, page }, testInfo) => {
  const gate = await world.createUser('GATE')
  for (const file of ['a01-4BZA94Z7YW.png', 'c01-00803J52QK.png']) {
    const png = await readFile(join(QR_TEST_DIR, file))
    await scanWithCamera(world, page, png, testInfo, gate, `E4b ${file}`, 0.7, async (gatePage) => {
      await expect(gatePage.getByRole('heading', { name: 'ACESSO NEGADO' }), `${file} lido pela câmera`).toBeVisible({ timeout: 15_000 })
      await expect(gatePage.getByText('Código inválido')).toBeVisible()
    })
  }
})

/**
 * Abre a Portaria num Chromium com o PNG como câmera, entra como GATE, toca em [ESCANEAR QR CODE] e
 * confere o resultado; depois, que a câmera parou (§16.5).
 */
async function scanWithCamera(
  world: World,
  page: Page,
  png: Buffer,
  testInfo: TestInfo,
  gate: Person,
  label: string,
  fraction: number,
  expectResult: (gatePage: Page) => Promise<void>,
): Promise<void> {
  // Fora de test-results: o Chromium não abre o vídeo num caminho com acentos (o do teste tem "câmera").
  const videoDir = await mkdtemp(join(tmpdir(), 'e2e-qr-'))
  const video = join(videoDir, 'qr.y4m')
  await writeFile(video, await qrVideo(page, png, fraction))

  // Um Chromium só da Portaria, com o vídeo como câmera e a permissão concedida. É o Chromium
  // completo (channel 'chromium'): o headless shell, padrão do Playwright, falhou no CI.
  const cameraBrowser = await chromium.launch({
    args: ['--use-fake-device-for-media-stream', `--use-file-for-fake-video-capture=${video}`],
    ...(process.env.E2E_CHROMIUM_PATH ? { executablePath: process.env.E2E_CHROMIUM_PATH } : { channel: 'chromium' }),
  })
  let gatePage: Page | undefined
  try {
    const context = await cameraBrowser.newContext({
      ...devices['Pixel 7'],
      baseURL: BASE_URL,
      ignoreHTTPSErrors: IGNORE_HTTPS_ERRORS,
      locale: 'pt-BR',
      timezoneId: OPERATION_TIMEZONE,
      permissions: ['camera'],
    })
    watchCsp(context, world.cspViolations)
    gatePage = await context.newPage()
    await login(gatePage, gate.email, gate.password)
    await expect(gatePage).toHaveURL('/portaria')
    await gatePage.getByRole('button', { name: 'ESCANEAR QR CODE' }).click()
    await expectResult(gatePage)
    // A câmera foi parada depois da leitura (§16.5).
    await expect(gatePage.getByLabel('Imagem da câmera')).toHaveCount(0)
  } catch (error) {
    // A captura automática é da página padrão; a da Portaria mostra o aviso de câmera, se houver.
    if (gatePage) {
      await testInfo.attach('portaria', { body: await gatePage.screenshot(), contentType: 'image/png' })
      const warning = gatePage.getByRole('status').filter({ hasText: /câmera/i })
      if (await warning.count()) console.log(`${label}: aviso de câmera: ${await warning.innerText()}`)
    }
    throw error
  } finally {
    await cameraBrowser.close()
    await rm(videoDir, { recursive: true, force: true })
  }
}
