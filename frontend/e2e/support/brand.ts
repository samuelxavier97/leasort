import { fileURLToPath } from 'node:url'
import type { BrowserContext, Locator } from '@playwright/test'

const fixture = (name: string) => fileURLToPath(new URL(`../fixtures/brand/${name}`, import.meta.url))

/**
 * Logotipos fictícios do tema de teste ("Resort Fictício das Águas"); nenhum logotipo real (D-114):
 * o comum (480 × 120), um muito largo (1800 × 120) e um muito alto (220 × 800).
 */
export const FICTIONAL_LOGOS = {
  default: fixture('logo.png'),
  wide: fixture('logo-largo.png'),
  tall: fixture('logo-alto.png'),
}
export const FICTIONAL_LOGO = FICTIONAL_LOGOS.default
export const FICTIONAL_RESORT = 'Resort Fictício das Águas'

export interface Theme {
  name: string
  color: string
  /** true é o logotipo comum. */
  logo: boolean | keyof typeof FICTIONAL_LOGOS
  /** Instalação de demonstração (D-125): a meta resort-demo com "true". */
  demo?: boolean
}

const escapeHtml = (value: string) =>
  value.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')

/**
 * Aplica um tema como o Nginx aplicaria na subida (D-115): reescreve as metas do index.html servido e
 * entrega o logotipo fictício em /brand/logo.png. Vale contra o `vite preview` e contra a pilha de
 * produção (que já traz um tema próprio, trocado aqui pelo do teste). Os cabeçalhos da resposta, com a
 * CSP, são mantidos.
 */
export async function applyTheme(context: BrowserContext, theme: Theme): Promise<void> {
  const logo = theme.logo === true ? 'default' : theme.logo
  if (logo) {
    await context.route('**/brand/logo.png', (route) => route.fulfill({ path: FICTIONAL_LOGOS[logo], contentType: 'image/png' }))
  }
  await context.route('**/*', async (route) => {
    if (route.request().resourceType() !== 'document') return route.fallback()
    const response = await route.fetch()
    const values: Record<string, string> = {
      'resort-name': escapeHtml(theme.name),
      'resort-brand-color': theme.color,
      'resort-brand-logo': theme.logo ? '/brand/logo.png' : '',
      'resort-demo': theme.demo ? 'true' : '',
    }
    let html = await response.text()
    for (const [name, content] of Object.entries(values)) {
      html = html.replace(new RegExp(`<meta name="${name}" content="[^"]*" />`), `<meta name="${name}" content="${content}" />`)
    }
    await route.fulfill({ response, body: html })
  })
}

type Rgb = [number, number, number]

function parseRgb(value: string): Rgb {
  const match = value.match(/rgba?\((\d+),\s*(\d+),\s*(\d+)/)
  if (!match) throw new Error(`cor inesperada: ${value}`)
  return [Number(match[1]), Number(match[2]), Number(match[3])]
}

function luminance(rgb: Rgb): number {
  const [r, g, b] = rgb.map((channel) => {
    const c = channel / 255
    return c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4
  })
  return 0.2126 * r + 0.7152 * g + 0.0722 * b
}

/** Razão de contraste da WCAG entre duas cores CSS `rgb(...)`. */
export function contrast(a: string, b: string): number {
  const [lighter, darker] = [luminance(parseRgb(a)), luminance(parseRgb(b))].sort((x, y) => y - x)
  return (lighter + 0.05) / (darker + 0.05)
}

/** Cores calculadas pelo navegador para um elemento. */
export async function colorsOf(locator: Locator) {
  return locator.evaluate((element) => {
    const style = getComputedStyle(element)
    return {
      color: style.color,
      background: style.backgroundColor,
      border: style.borderBottomColor,
      borderWidth: style.borderBottomWidth,
      fontWeight: Number(style.fontWeight),
    }
  })
}
