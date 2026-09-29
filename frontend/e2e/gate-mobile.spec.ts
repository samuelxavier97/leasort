import type { Page } from '@playwright/test'
import { applyTheme, colorsOf, FICTIONAL_RESORT } from './support/brand.ts'
import { fakeCpf, operationDate, recordSensitive } from './support/data.ts'
import { expect, login, test } from './support/fixtures.ts'
import { openTyping, typeCode } from './support/screens.ts'

const noHorizontalScroll = (page: Page) =>
  page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth)

/**
 * E9: a Portaria no celular (viewport do Pixel 7), com um tema cuja cor principal é o mesmo verde do
 * "liberado": nenhuma tela rola na horizontal, a faixa do resultado usa as cores fixas (D-120) e os
 * botões das telas de resultado não usam a cor do cliente.
 */
test('E9: Portaria no celular, com LIBERADO e NEGADO nas cores fixas e sem rolagem horizontal', async ({ world }) => {
  const prospector = await world.createUser('PROSPECTOR')
  const gate = await world.createUser('GATE')
  const leadName = `Lead E2E Portaria ${world.suffix}`
  const lead = await world.createLead({
    name: leadName,
    cpf: fakeCpf(),
    phone: '(11) 90000-0005',
    birthDate: '1988-06-21',
    prospectorId: prospector.prospectorId!,
  })
  const prospectorApi = await world.apiAs(prospector)
  const visit = await prospectorApi.json<{ invitation: { id: string } }>('POST', '/api/visits', {
    leadId: lead.id,
    scheduledDate: operationDate(),
    notes: null,
    hostNotes: null,
    companions: [
      { name: 'Acompanhante Fictício Um', relationship: 'SPOUSE', birthDate: '1989-02-02', cpf: null },
      { name: 'Acompanhante Fictício Dois', relationship: 'CHILD', birthDate: '2015-08-08', cpf: null },
    ],
  })
  const { code } = (await (await prospectorApi.get(`/api/invitations/${visit.invitation.id}`)).json()) as { code: string }
  recordSensitive(code)

  const context = await world.newContext('GATE')
  // Cor principal igual ao verde do "liberado": mesmo assim, o resultado não se confunde com ela.
  await applyTheme(context, { name: FICTIONAL_RESORT, color: '#15803d', logo: true })
  const page = await context.newPage()
  await login(page, gate.email, gate.password)
  await expect(page).toHaveURL('/portaria')
  expect(page.viewportSize()!.width).toBeLessThan(500)
  expect(await noHorizontalScroll(page), 'início').toBe(true)
  // Cabeçalho numa linha só no celular: a identidade e o botão Menu, sem o menu quebrado em linhas (A8).
  const header = (await page.getByRole('banner').boundingBox())!
  expect(header.height, 'altura do cabeçalho').toBeLessThanOrEqual(72)
  await expect(page.getByRole('navigation', { name: 'Menu principal' })).toBeHidden()
  await expect(page.getByRole('button', { name: 'Menu' })).toBeVisible()

  await openTyping(page)
  await typeCode(page, code)
  const authorized = page.getByRole('heading', { name: 'ACESSO LIBERADO' })
  await expect(authorized).toBeVisible()
  expect((await colorsOf(authorized)).background).toBe('rgb(21, 128, 61)')
  expect(await noHorizontalScroll(page), 'liberado').toBe(true)
  const confirm = await colorsOf(page.getByRole('button', { name: 'CONFIRMAR ENTRADA' }))
  expect(confirm.background, 'CONFIRMAR ENTRADA sem a cor do cliente').not.toBe('rgb(21, 128, 61)')
  await page.getByRole('button', { name: 'CONFIRMAR ENTRADA' }).click()
  await expect(page.getByText(`Entrada de ${leadName} registrada.`)).toBeVisible()

  // O mesmo código de novo: já utilizado.
  await typeCode(page, code)
  const denied = page.getByRole('heading', { name: 'ACESSO NEGADO' })
  await expect(denied).toBeVisible()
  expect((await colorsOf(denied)).background).toBe('rgb(185, 28, 28)')
  await expect(page.getByText('Convite já utilizado', { exact: true })).toBeVisible()
  expect(await noHorizontalScroll(page), 'negado').toBe(true)
  const again = await colorsOf(page.getByRole('button', { name: 'NOVA VALIDAÇÃO' }))
  expect(again.background, 'NOVA VALIDAÇÃO sem a cor do cliente').not.toBe('rgb(21, 128, 61)')
})
