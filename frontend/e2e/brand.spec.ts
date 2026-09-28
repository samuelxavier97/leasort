import type { Page } from '@playwright/test'
import { applyTheme, colorsOf, contrast, FICTIONAL_RESORT, type Theme } from './support/brand.ts'
import { adminCredentials, expect, login, test, type World } from './support/fixtures.ts'

/**
 * E7: tema do cliente (D-115 a D-119) aplicado pelas metas, como o Nginx faz na subida. Confere a
 * hierarquia da identidade no cabeçalho e na aba, o contraste da cor principal medido pelo navegador e o
 * item ativo do menu com uma cor clara. Todos os contextos reprovam violação de CSP.
 */
async function adminWithTheme(world: World, theme: Theme): Promise<Page> {
  const context = await world.newContext('ADMIN')
  await applyTheme(context, theme)
  const page = await context.newPage()
  const { email, password } = adminCredentials()
  await login(page, email, password)
  await expect(page).toHaveURL('/dashboard')
  await page.getByRole('link', { name: 'Leads', exact: true }).click()
  await expect(page.getByRole('heading', { level: 1, name: 'Leads' })).toBeVisible()
  return page
}

const identity = (page: Page) => page.getByRole('banner').locator('a[href="/"]')

test('E7a: sem tema, o cabeçalho e a aba mostram o Resortric', async ({ world }) => {
  const page = await adminWithTheme(world, { name: '', color: '', logo: false })
  const logo = identity(page).getByRole('img', { name: 'Resortric' })
  await expect(logo).toHaveAttribute('src', '/resortric.svg')
  await expect.poll(() => logo.evaluate((image: HTMLImageElement) => image.naturalWidth)).toBeGreaterThan(0)
  await expect(page).toHaveTitle('Leads · Resortric')
  await expect(page.getByRole('contentinfo').getByRole('img', { name: 'Resortric' })).toBeVisible()
})

test('E7b: só com RESORT_NAME, o nome aparece em texto, nunca "Resortric", no cabeçalho e na aba', async ({ world }) => {
  const page = await adminWithTheme(world, { name: FICTIONAL_RESORT, color: '', logo: false })
  await expect(identity(page)).toHaveText(FICTIONAL_RESORT)
  await expect(page.getByRole('banner').getByRole('img', { name: 'Resortric' })).toHaveCount(0)
  await expect(page).toHaveTitle(`Leads · ${FICTIONAL_RESORT}`)
})

test('E7c: nome, cor escura e logotipo: o logotipo por <img>, e o botão principal com contraste de 4,5:1', async ({ world }) => {
  const page = await adminWithTheme(world, { name: FICTIONAL_RESORT, color: '#1e3a5f', logo: true })
  const logo = identity(page).getByRole('img', { name: FICTIONAL_RESORT })
  await expect(logo).toHaveAttribute('src', '/brand/logo.png')
  await expect.poll(() => logo.evaluate((image: HTMLImageElement) => image.naturalWidth)).toBe(480)
  await expect(identity(page)).not.toHaveText(FICTIONAL_RESORT)
  await expect(page).toHaveTitle(`Leads · ${FICTIONAL_RESORT}`)

  const button = await colorsOf(page.getByRole('button', { name: 'Novo Lead' }))
  expect(button.background).toBe('rgb(30, 58, 95)')
  expect(button.color).toBe('rgb(255, 255, 255)')
  expect(contrast(button.background, button.color)).toBeGreaterThanOrEqual(4.5)
  // Faixa de 4 px na cor principal no topo (D-117).
  const stripe = page.locator('[aria-hidden="true"].h-1').first()
  expect((await colorsOf(stripe)).background).toBe('rgb(30, 58, 95)')
})

test('E7d: cor clara (#f5d90a): texto preto no botão e item ativo do menu reconhecível sem depender da cor', async ({ world }) => {
  const page = await adminWithTheme(world, { name: FICTIONAL_RESORT, color: '#f5d90a', logo: false })
  const button = await colorsOf(page.getByRole('button', { name: 'Novo Lead' }))
  expect(button.background).toBe('rgb(245, 217, 10)')
  expect(button.color).toBe('rgb(0, 0, 0)')
  expect(contrast(button.background, button.color)).toBeGreaterThanOrEqual(4.5)
  // Borda escurecida até 3:1 contra o branco, para o botão não sumir no fundo (D-118).
  expect(contrast(button.border, 'rgb(255, 255, 255)')).toBeGreaterThanOrEqual(3)

  const menu = page.getByRole('navigation', { name: 'Menu principal' })
  const active = await colorsOf(menu.getByRole('link', { name: 'Leads', exact: true }))
  const inactive = await colorsOf(menu.getByRole('link', { name: 'Visitas', exact: true }))
  // Três sinais: negrito, fundo e a linha na cor de borda (3:1 contra o branco).
  expect(active.fontWeight).toBeGreaterThanOrEqual(600)
  expect(inactive.fontWeight).toBeLessThan(600)
  expect(active.background).not.toBe(inactive.background)
  expect(active.borderWidth).toBe('2px')
  expect(contrast(active.border, 'rgb(255, 255, 255)')).toBeGreaterThanOrEqual(3)
})
