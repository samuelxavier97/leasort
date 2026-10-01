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

/** Dourado do Resortric (D-126), só sem tema de cliente. */
const GOLD = 'rgb(176, 141, 87)'

/** O trecho final da faixa do topo (D-126): dourado sem tema; com tema de cliente, a própria cor. */
async function stripeEnd(page: Page) {
  return (await colorsOf(page.getByTestId('brand-stripe').locator('div'))).background
}

const favicon = (page: Page) => page.locator('link[rel="icon"]').getAttribute('href')

test('E7a: sem tema, o cabeçalho e a aba mostram o Resortric, com o dourado e o tingimento da cor padrão', async ({ world }) => {
  const page = await adminWithTheme(world, { name: '', color: '', logo: false })
  const logo = identity(page).getByRole('img', { name: 'Resortric' })
  await expect(logo).toHaveAttribute('src', '/resortric.svg')
  await expect.poll(() => logo.evaluate((image: HTMLImageElement) => image.naturalWidth)).toBeGreaterThan(0)
  await expect(page).toHaveTitle('Leads · Resortric')
  await expect(page.getByRole('contentinfo').getByRole('img', { name: 'Resortric' })).toHaveAttribute('src', '/resortric.svg')
  expect(await favicon(page)).toBe('/favicon.svg')
  // Faixa na cor padrão com o trecho final dourado (D-126).
  expect((await colorsOf(page.getByTestId('brand-stripe'))).background).toBe('rgb(31, 78, 121)')
  expect(await stripeEnd(page)).toBe(GOLD)
  // Fundo areia com 3,5% da principal; cabeçalho e tabela continuam brancos (D-127).
  const body = await colorsOf(page.getByRole('banner').locator('..'))
  expect(body.background).toBe('rgb(242, 242, 240)')
  expect((await colorsOf(page.getByRole('banner'))).background).toBe('rgb(255, 255, 255)')
  // Texto secundário com 4,5:1 sobre o fundo tingido.
  const muted = await page.evaluate(() => {
    const probe = document.createElement('p')
    probe.className = 'text-muted-foreground'
    document.body.append(probe)
    return getComputedStyle(probe).color
  })
  expect(contrast(muted, body.background)).toBeGreaterThanOrEqual(4.5)
})

test('E7b: só com RESORT_NAME, o nome aparece em texto, nunca "Resortric", no cabeçalho e na aba', async ({ world }) => {
  const page = await adminWithTheme(world, { name: FICTIONAL_RESORT, color: '', logo: false })
  await expect(identity(page)).toHaveText(FICTIONAL_RESORT)
  await expect(page.getByRole('banner').getByRole('img', { name: 'Resortric' })).toHaveCount(0)
  await expect(page).toHaveTitle(`Leads · ${FICTIONAL_RESORT}`)
  // Só o nome não é tema de cliente: o dourado continua (D-126).
  expect(await stripeEnd(page)).toBe(GOLD)
  expect(await favicon(page)).toBe('/favicon.svg')
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
  // Faixa de 4 px na cor principal no topo (D-117), sem o trecho dourado com tema de cliente (D-126).
  expect((await colorsOf(page.getByTestId('brand-stripe'))).background).toBe('rgb(30, 58, 95)')
  expect(await stripeEnd(page)).toBe('rgb(30, 58, 95)')
  await expect(page.getByRole('contentinfo').getByRole('img', { name: 'Resortric' })).toHaveAttribute('src', '/resortric-mono.svg')
  expect(await favicon(page)).toBe('/favicon-mono.svg')
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
  // Três sinais: negrito, fundo e a linha na cor de borda (3:1 contra o branco e contra o fundo de 14%).
  expect(active.fontWeight).toBeGreaterThanOrEqual(600)
  expect(inactive.fontWeight).toBeLessThan(600)
  expect(active.background).not.toBe(inactive.background)
  expect(active.borderWidth).toBe('2px')
  expect(contrast(active.border, 'rgb(255, 255, 255)')).toBeGreaterThanOrEqual(3)
  expect(contrast(active.border, active.background)).toBeGreaterThanOrEqual(3)
  expect(contrast(active.color, active.background)).toBeGreaterThanOrEqual(4.5)
  // Com cor clara, a faixa fica toda na cor do cliente, sem dourado (D-126).
  expect(await stripeEnd(page)).toBe('rgb(245, 217, 10)')
})

test('E7e: login com o tema: o logotipo em destaque, a faixa na cor principal e o Resortric no rodapé', async ({ world }) => {
  const context = await world.newContext('ADMIN')
  await applyTheme(context, { name: FICTIONAL_RESORT, color: '#1e3a5f', logo: true })
  const page = await context.newPage()
  await page.goto('/login')
  await expect(page.getByRole('heading', { name: 'Entrar' })).toBeVisible()
  const logo = page.getByTestId('auth-identity').getByRole('img', { name: FICTIONAL_RESORT })
  await expect.poll(() => logo.evaluate((image: HTMLImageElement) => image.naturalWidth)).toBe(480)
  await expect(page).toHaveTitle(`Entrar · ${FICTIONAL_RESORT}`)
  await expect(page.getByRole('contentinfo').getByRole('img', { name: 'Resortric' })).toHaveAttribute('src', '/resortric-mono.svg')
  const button = await colorsOf(page.getByRole('button', { name: 'Entrar' }))
  expect(button.background).toBe('rgb(30, 58, 95)')
  expect(contrast(button.background, button.color)).toBeGreaterThanOrEqual(4.5)
  // A linha curta sob a identidade usa a cor do cliente, sem dourado (D-126).
  expect((await colorsOf(page.getByTestId('auth-accent'))).background).toBe('rgb(30, 58, 95)')
})

test('E7g: login sem tema: o logotipo do Resortric, a linha curta e o fim da faixa em dourado (D-126)', async ({ world }) => {
  const context = await world.newContext('ADMIN')
  await applyTheme(context, { name: '', color: '', logo: false })
  const page = await context.newPage()
  await page.goto('/login')
  await expect(page.getByRole('heading', { name: 'Entrar' })).toBeVisible()
  await expect(page.getByTestId('auth-identity').getByRole('img', { name: 'Resortric' })).toHaveAttribute('src', '/resortric.svg')
  expect((await colorsOf(page.getByTestId('auth-accent'))).background).toBe(GOLD)
  expect(await stripeEnd(page)).toBe(GOLD)
  expect(await favicon(page)).toBe('/favicon.svg')
})


/**
 * E7f: instalação de demonstração (D-125). Com a meta resort-demo, como o Nginx a preenche com
 * DEMO_INSTANCE=true, o rodapé do login e o das telas mostram "Ambiente de demonstração" ao lado do
 * Resortric; sem ela, nenhuma etiqueta.
 */
test('E7f: instalação de demonstração: a etiqueta no rodapé do login e das telas; sem ela, nada', async ({ world }) => {
  const context = await world.newContext('ADMIN')
  await applyTheme(context, { name: FICTIONAL_RESORT, color: '#1e3a5f', logo: true, demo: true })
  const page = await context.newPage()
  await page.goto('/login')
  const loginFooter = page.getByRole('contentinfo')
  await expect(loginFooter.getByRole('img', { name: 'Resortric' })).toBeVisible()
  await expect(loginFooter.getByText('Ambiente de demonstração')).toBeVisible()
  const { email, password } = adminCredentials()
  await login(page, email, password)
  await expect(page).toHaveURL('/dashboard')
  const footer = page.getByRole('contentinfo')
  await expect(footer.getByRole('img', { name: 'Resortric' })).toBeVisible()
  await expect(footer.getByText('Ambiente de demonstração')).toBeVisible()

  const plain = await adminWithTheme(world, { name: FICTIONAL_RESORT, color: '#1e3a5f', logo: true })
  await expect(plain.getByRole('contentinfo').getByRole('img', { name: 'Resortric' })).toBeVisible()
  await expect(plain.getByText('Ambiente de demonstração')).toHaveCount(0)
})
