import type { Page } from '@playwright/test'
import { applyTheme, colorsOf, contrast, FICTIONAL_RESORT, type Theme } from './support/brand.ts'
import { fakeCpf, operationDate, recordSensitive } from './support/data.ts'
import { adminCredentials, expect, login, test, type World } from './support/fixtures.ts'

const THEMES: Theme[] = [
  { name: '', color: '', logo: false },
  { name: FICTIONAL_RESORT, color: '#1e3a5f', logo: true },
  { name: FICTIONAL_RESORT, color: '#f5d90a', logo: false },
]

/** Visita de hoje com um acompanhante e a entrada registrada pela API, mais uma negativa por código inválido. */
async function arrivalAndDenial(world: World) {
  const prospector = await world.createUser('PROSPECTOR')
  const gate = await world.createUser('GATE')
  const leadName = `Lead E2E Dashboard ${world.suffix}`
  const lead = await world.createLead({
    name: leadName,
    cpf: fakeCpf(),
    phone: '(11) 90000-0010',
    birthDate: '1984-02-11',
    prospectorId: prospector.prospectorId!,
  })
  const prospectorApi = await world.apiAs(prospector)
  const visit = await prospectorApi.json<{ invitation: { id: string } }>('POST', '/api/visits', {
    leadId: lead.id,
    scheduledDate: operationDate(),
    notes: null,
    hostNotes: null,
    companions: [{ name: 'Acompanhante Fictício do Dashboard', relationship: 'SPOUSE', birthDate: '1985-05-05', cpf: null }],
  })
  const { code } = (await (await prospectorApi.get(`/api/invitations/${visit.invitation.id}`)).json()) as { code: string }
  recordSensitive(code)
  const gateApi = await world.apiAs(gate)
  const validated = await gateApi.json<{ result: string; invitationId: string; companions: { id: string }[] }>(
    'POST',
    '/api/access/validate',
    { code },
  )
  expect(validated.result).toBe('AUTHORIZED')
  await gateApi.json('POST', '/api/access/register', {
    invitationId: validated.invitationId,
    presentCompanionIds: validated.companions.map((companion) => companion.id),
  })
  const denied = await gateApi.json<{ result: string; denialReason: string }>('POST', '/api/access/validate', { code: 'ZZZZZZZZZZ' })
  expect(denied.denialReason).toBe('INVALID_CODE')
  return { prospector, leadName }
}

async function adminDashboard(world: World, theme: Theme, viewport?: { width: number; height: number }): Promise<Page> {
  const context = await world.newContext('ADMIN')
  await applyTheme(context, theme)
  const page = await context.newPage()
  if (viewport) await page.setViewportSize(viewport)
  const { email, password } = adminCredentials()
  await login(page, email, password)
  await expect(page).toHaveURL('/dashboard')
  await expect(page.getByRole('region', { name: 'Desempenho por Prospector' })).toBeVisible()
  return page
}

/**
 * E10 (D-128, D-129): o dashboard do ADMIN com a faixa, os cartões, "Hoje na sala de vendas", o desempenho
 * por Prospector e as negativas por motivo. Confere o menu do ADMIN numa linha a 1280 px (com e sem tema), o
 * contraste do texto sobre a faixa medido pelo navegador (cor padrão, escura e clara), a chegada e a
 * negativa feitas agora aparecendo, o filtro aplicado pelo ranking e nenhuma rolagem horizontal a 390 px.
 */
test('E10: dashboard do ADMIN com faixa, chegada e negativa de hoje, menu em uma linha a 1280 px e sem rolagem a 390 px', async ({
  world,
}) => {
  const { prospector, leadName } = await arrivalAndDenial(world)

  for (const theme of THEMES) {
    const page = await adminDashboard(world, theme)
    expect(page.viewportSize()?.width).toBe(1280)

    // Menu do ADMIN numa linha só: os itens e o submenu "Administração" na mesma altura (D-128).
    const nav = page.getByRole('navigation', { name: 'Menu principal' })
    await expect(nav.getByRole('button', { name: 'Administração' })).toBeVisible()
    const rows = await nav.evaluate((element) => new Set([...element.children].map((child) => Math.round(child.getBoundingClientRect().top))).size)
    expect(rows, `menu numa linha (${theme.color || 'sem tema'})`).toBe(1)
    expect((await page.getByRole('banner').boundingBox())!.height).toBeLessThanOrEqual(72)

    // Texto sobre a faixa, inclusive o secundário, com 4,5:1 (D-118).
    const band = page.getByTestId('dashboard-band')
    const background = (await colorsOf(band)).background
    for (const text of [band.getByText(/^(Bom dia|Boa tarde|Boa noite), /), band.getByText(/^Período: /), band.getByRole('heading', { level: 1 })]) {
      expect(contrast((await colorsOf(text)).color, background), theme.color || 'sem tema').toBeGreaterThanOrEqual(4.5)
    }
  }

  // Sem tema: a chegada de agora no quadro de hoje, o ranking e o filtro pelo clique, e a negativa por motivo.
  const page = await adminDashboard(world, THEMES[0])
  const ranking = page.getByRole('region', { name: 'Desempenho por Prospector' })
  await ranking.getByRole('button', { name: `Filtrar por ${prospector.name}` }).click()
  await expect(page.getByLabel('Prospector', { exact: true })).toHaveValue(/.+/)
  const today = page.getByRole('region', { name: 'Hoje na sala de vendas' })
  const item = today.getByRole('listitem').filter({ hasText: leadName })
  await expect(item).toContainText(prospector.name)
  await expect(item).toContainText('1 de 1 acompanhante presente')
  await expect(item).toContainText(/Chegou às \d{2}:\d{2}/)
  await expect(page.getByTestId('dashboard-band')).toContainText('Hoje há 1 visita; 1 já chegou.')
  await expect(ranking.getByRole('button', { name: `Tirar o filtro de ${prospector.name}` })).toHaveAttribute('aria-pressed', 'true')
  await ranking.getByRole('button', { name: `Tirar o filtro de ${prospector.name}` }).click()
  await expect(page.getByLabel('Prospector', { exact: true })).toHaveValue('')

  const denials = page.getByRole('region', { name: 'Por que a portaria negou' })
  await denials.getByText('Ver tabela').click()
  const invalid = denials.getByRole('row').filter({ hasText: 'Código inválido' })
  expect(Number(await invalid.getByRole('cell').nth(1).textContent())).toBeGreaterThanOrEqual(1)
  await expect(page.getByText('Acessos por dia')).toHaveCount(0)

  // Celular: a faixa e os cartões sem rolagem horizontal.
  const mobile = await adminDashboard(world, THEMES[2], { width: 390, height: 844 })
  await expect(mobile.getByTestId('dashboard-band')).toBeVisible()
  expect(await mobile.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth)).toBe(true)
})
