import { applyTheme, FICTIONAL_RESORT, type Theme } from './support/brand.ts'
import { worstCaseArrival } from './support/arrival.ts'
import { expect, login, test } from './support/fixtures.ts'

/** Páginas de um PDF gerado pelo Chromium (cada página é um objeto /Type /Page). */
function pageCount(pdf: Buffer): number {
  return (pdf.toString('latin1').match(/\/Type\s*\/Page(?![s\w])/g) ?? []).length
}

/** 12 mm e 45 mm em px CSS (96 px por polegada). */
const MAX_LOGO_HEIGHT = (12 * 96) / 25.4
const MAX_LOGO_WIDTH = (45 * 96) / 25.4

/**
 * E8: a ficha no pior caso (6 acompanhantes e 2.000 caracteres de observações, D-097) sai em uma
 * página A4, sem logotipo e com o logotipo fictício comum, muito largo e muito alto (D-117). O PDF é o
 * da impressão do Chromium, com o @page da ficha (A4, 12 mm). Automatiza a conferência manual da D-097.
 */
test('E8: a ficha no pior caso sai em uma página A4, com e sem logotipo', async ({ world }) => {
  const { host, visitId } = await worstCaseArrival(world)
  const themes: [string, Theme][] = [
    ['sem logotipo', { name: FICTIONAL_RESORT, color: '#1e3a5f', logo: false }],
    ['logotipo comum', { name: FICTIONAL_RESORT, color: '#1e3a5f', logo: true }],
    ['logotipo muito largo', { name: FICTIONAL_RESORT, color: '#1e3a5f', logo: 'wide' }],
    ['logotipo muito alto', { name: FICTIONAL_RESORT, color: '#1e3a5f', logo: 'tall' }],
  ]
  for (const [label, theme] of themes) {
    const context = await world.newContext('HOST')
    await applyTheme(context, theme)
    const page = await context.newPage()
    await login(page, host.email, host.password)
    await expect(page).toHaveURL('/chegadas')
    await page.goto(`/chegadas/${visitId}/ficha`)
    await expect(page.getByRole('heading', { name: /^Ficha da visita/ })).toBeVisible()
    await expect(page.getByRole('region', { name: 'Acompanhantes presentes' }).getByRole('listitem')).toHaveCount(4)
    await expect(page.getByRole('region', { name: 'Acompanhantes ausentes' }).getByRole('listitem')).toHaveCount(2)

    await page.emulateMedia({ media: 'print' })
    const identity = page.getByTestId('sheet-identity')
    if (theme.logo) {
      const logo = identity.getByRole('img', { name: FICTIONAL_RESORT })
      await expect.poll(() => logo.evaluate((image: HTMLImageElement) => image.naturalWidth), { message: label }).toBeGreaterThan(0)
      const box = (await logo.boundingBox())!
      expect(box.height, `${label}: altura do logotipo`).toBeLessThanOrEqual(MAX_LOGO_HEIGHT + 0.5)
      expect(box.width, `${label}: largura do logotipo`).toBeLessThanOrEqual(MAX_LOGO_WIDTH + 0.5)
    } else {
      await expect(identity).toHaveText(FICTIONAL_RESORT)
    }
    const pdf = await page.pdf({ preferCSSPageSize: true, printBackground: true })
    expect(pageCount(pdf), `${label}: páginas do PDF`).toBe(1)
    await context.close()
  }
})
