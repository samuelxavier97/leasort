import { readFile } from 'node:fs/promises'
import type { Page } from '@playwright/test'
import { fakeCpf, operationDate } from './support/data.ts'
import { expect, login, test, type World } from './support/fixtures.ts'
import { invitationCode } from './support/screens.ts'
import { applyTheme, FICTIONAL_RESORT } from './support/brand.ts'
import { readPng } from './support/png.ts'

/** Convite de hoje criado pela API; devolve o e-mail e a senha do Prospector e o id do convite. */
async function invitation(world: World): Promise<{ prospector: { email: string; password: string }; id: string }> {
  const prospector = await world.createUser('PROSPECTOR')
  const lead = await world.createLead({
    name: `Lead E2E ${world.suffix}`,
    cpf: fakeCpf(),
    phone: '(11) 90000-0003',
    birthDate: '1985-02-14',
    prospectorId: prospector.prospectorId!,
  })
  const visit = await (await world.apiAs(prospector)).json<{ invitation: { id: string } }>('POST', '/api/visits', {
    leadId: lead.id,
    scheduledDate: operationDate(),
    notes: null,
    hostNotes: null,
    companions: [],
  })
  return { prospector, id: visit.invitation.id }
}

async function expectInvitationImage(blankPage: Page, bytes: Buffer, fileName: string, code: string): Promise<void> {
  expect(fileName).toBe(`convite-${code.slice(0, 5)}-${code.slice(5)}.png`)
  const png = await readPng(blankPage, bytes)
  expect({ width: png.width, height: png.height }).toEqual({ width: 1080, height: 1440 })
  expect(png.qrText).toBe(`RSV:${code}`)
}

/**
 * E6: a imagem de compartilhamento do convite (D-087) é gerada sem violar a CSP (D-107): "Baixar" e
 * "Compartilhar" sem a Web Share API baixam o PNG; com a Web Share API, o arquivo compartilhado é o
 * mesmo PNG. Nos três casos, a imagem tem 1080 × 1440 px e o QR traz exatamente RSV:<código>.
 */
test('E6: Baixar e Compartilhar geram o PNG do convite com o código certo, sem violar a CSP', async ({ world, page }) => {
  const { prospector, id } = await invitation(world)

  const invitationPage = await world.loggedIn('PROSPECTOR', prospector)
  await invitationPage.goto(`/convites/${id}`)
  const code = await invitationCode(invitationPage)

  for (const button of ['Baixar', 'Compartilhar']) {
    const download = invitationPage.waitForEvent('download')
    await invitationPage.getByRole('button', { name: button, exact: true }).click()
    const file = await download
    await expectInvitationImage(page, await readFile((await file.path())!), file.suggestedFilename(), code)
  }

  // Com a Web Share API (como no celular): o arquivo entregue ao navegador é o mesmo PNG.
  const sharing = await world.newContext('PROSPECTOR')
  await sharing.addInitScript(() => {
    const shared: { name: string; base64: string }[] = []
    Object.assign(window, { __shared: shared })
    Object.assign(navigator, {
      canShare: (data: ShareData) => Array.isArray(data.files) && data.files.length > 0,
      share: async (data: ShareData) => {
        const file = data.files![0]
        const bytes = new Uint8Array(await file.arrayBuffer())
        let binary = ''
        for (const byte of bytes) binary += String.fromCharCode(byte)
        shared.push({ name: file.name, base64: btoa(binary) })
      },
    })
  })
  const sharingPage = await sharing.newPage()
  await login(sharingPage, prospector.email, prospector.password)
  await expect(sharingPage).toHaveURL('/dashboard')
  await sharingPage.goto(`/convites/${id}`)
  await expect(sharingPage.getByTestId('invitation-code')).toHaveText(`${code.slice(0, 5)}-${code.slice(5)}`)
  await sharingPage.getByRole('button', { name: 'Compartilhar', exact: true }).click()
  await expect.poll(() => sharingPage.evaluate(() => (window as unknown as { __shared: unknown[] }).__shared.length)).toBe(1)
  const shared = await sharingPage.evaluate(() => (window as unknown as { __shared: { name: string; base64: string }[] }).__shared[0])
  await expectInvitationImage(page, Buffer.from(shared.base64, 'base64'), shared.name, code)
})

/**
 * E6b: com o logotipo do cliente (D-117), o canvas desenha o /brand/logo.png da própria origem sem
 * ficar "sujo" (o toBlob funcionaria mal com uma imagem de outra origem), e o QR continua legível,
 * também com o logotipo muito largo e o muito alto.
 */
test('E6b: com o logotipo do cliente, o PNG do convite continua com o QR legível', async ({ world, page }) => {
  const { prospector, id } = await invitation(world)
  for (const logo of [true, 'wide', 'tall'] as const) {
    const context = await world.newContext('PROSPECTOR')
    await applyTheme(context, { name: FICTIONAL_RESORT, color: '#1e3a5f', logo })
    const invitationPage = await context.newPage()
    await login(invitationPage, prospector.email, prospector.password)
    await expect(invitationPage).toHaveURL('/dashboard')
    await invitationPage.goto(`/convites/${id}`)
    const code = await invitationCode(invitationPage)
    const download = invitationPage.waitForEvent('download')
    await invitationPage.getByRole('button', { name: 'Baixar', exact: true }).click()
    const file = await download
    await expectInvitationImage(page, await readFile((await file.path())!), file.suggestedFilename(), code)
    await context.close()
  }
})

