/** Nome do produto (D-116): o único lugar em que ele é escrito. */
export const PRODUCT_NAME = 'Resortric'

/** Cor principal padrão do Resortric (D-116), usada sem `BRAND_COLOR` ou com valor inválido. */
export const DEFAULT_BRAND_COLOR = '#1f4e79'

/** Único endereço aceito para o logotipo: a cópia validada pelo Nginx na subida (D-115, D-119). */
export const BRAND_LOGO_URL = '/brand/logo.png'

/** Logotipo tipográfico do Resortric (D-116). */
export const PRODUCT_LOGO_URL = '/resortric.svg'

const HEX_COLOR = /^#[0-9a-fA-F]{6}$/

export interface Brand {
  /** `RESORT_NAME`, ou null sem ele. */
  resortName: string | null
  /** Cor principal em `#rrggbb`, minúscula. */
  color: string
  /** `/brand/logo.png`, ou null sem logotipo. */
  logoUrl: string | null
}

function meta(doc: Document, name: string): string {
  return doc.querySelector<HTMLMetaElement>(`meta[name="${name}"]`)?.content.trim() ?? ''
}

/**
 * Tema do cliente (D-115). As metas vêm do `index.html` que o container do Nginx gera na subida: o
 * nome (`resort-name`, D-087, já escapado para HTML), a cor e o logotipo, os dois já validados lá. Aqui
 * se valida de novo: um valor fora do formato vale o padrão.
 */
export function readBrand(doc: Document = document): Brand {
  const color = meta(doc, 'resort-brand-color')
  const logo = meta(doc, 'resort-brand-logo')
  return {
    resortName: meta(doc, 'resort-name') || null,
    color: HEX_COLOR.test(color) ? color.toLowerCase() : DEFAULT_BRAND_COLOR,
    logoUrl: logo === BRAND_LOGO_URL ? BRAND_LOGO_URL : null,
  }
}

/** Etiqueta do rodapé numa instalação de demonstração (D-125). */
export const DEMO_LABEL = 'Ambiente de demonstração'

/**
 * Instalação de demonstração (D-125): a meta `resort-demo` é "true" quando o Nginx sobe com
 * `DEMO_INSTANCE=true`. Qualquer outro valor não liga a etiqueta.
 */
export function isDemoInstance(doc: Document = document): boolean {
  return meta(doc, 'resort-demo') === 'true'
}

/** Tema da página atual; lido a cada chamada, porque as metas não mudam depois da subida. */
export function currentBrand(): Brand {
  return readBrand(document)
}

/** Nome em texto da identidade (D-117): `RESORT_NAME` ou, sem ele, Resortric. */
export function brandName(brand: Brand): string {
  return brand.resortName ?? PRODUCT_NAME
}

/** Título da aba (D-117): "Tela · RESORT_NAME" ou "Tela · Resortric". */
export function documentTitle(screen: string | null, brand: Brand): string {
  return screen ? `${screen} · ${brandName(brand)}` : brandName(brand)
}

// Cores e contraste (D-118), pelas fórmulas da WCAG 2.x.

type Rgb = [number, number, number]

const WHITE: Rgb = [255, 255, 255]
const BLACK: Rgb = [0, 0, 0]

function toRgb(hex: string): Rgb {
  return [1, 3, 5].map((index) => parseInt(hex.slice(index, index + 2), 16)) as Rgb
}

function toHex(rgb: Rgb): string {
  return `#${rgb.map((channel) => channel.toString(16).padStart(2, '0')).join('')}`
}

function luminance([r, g, b]: Rgb): number {
  const linear = (channel: number) => {
    const c = channel / 255
    return c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4
  }
  return 0.2126 * linear(r) + 0.7152 * linear(g) + 0.0722 * linear(b)
}

/** Razão de contraste entre duas cores `#rrggbb`, de 1 a 21. */
export function contrastRatio(a: string, b: string): number {
  const [lighter, darker] = [luminance(toRgb(a)), luminance(toRgb(b))].sort((x, y) => y - x)
  return (lighter + 0.05) / (darker + 0.05)
}

/** Mistura cada canal em direção a `target` (0 = a cor, 1 = o alvo). */
function mix(rgb: Rgb, target: Rgb, amount: number): Rgb {
  return rgb.map((channel, index) => Math.round(channel + (target[index] - channel) * amount)) as Rgb
}

/** Mínimos da D-118. */
export const TEXT_CONTRAST = 4.5
export const EDGE_CONTRAST = 3
const HOVER_MIX = 0.12

export interface BrandPalette {
  primary: string
  /** Texto sobre a cor: branco se atingir 4,5:1; senão, preto puro (sempre atinge). */
  foreground: string
  /** Escurece com texto branco e clareia com texto preto: o contraste nunca cai. */
  hover: string
  /** Borda e indicador: a cor com 3:1 contra o branco, ou a cor escurecida até 3:1. */
  edge: string
}

export function brandPalette(color: string): BrandPalette {
  const rgb = toRgb(color)
  const whiteText = contrastRatio(color, '#ffffff') >= TEXT_CONTRAST
  let edge = rgb
  for (let step = 1; contrastRatio(toHex(edge), '#ffffff') < EDGE_CONTRAST; step++) {
    edge = mix(rgb, BLACK, step / 20)
  }
  return {
    primary: color,
    foreground: whiteText ? '#ffffff' : '#000000',
    hover: toHex(mix(rgb, whiteText ? BLACK : WHITE, HOVER_MIX)),
    edge: toHex(edge),
  }
}

/** Aplica a cor principal no `:root` por CSSOM, sem `<style>` inline (a CSP, D-107). */
export function applyBrand(root: HTMLElement, brand: Brand): void {
  const palette = brandPalette(brand.color)
  root.style.setProperty('--primary', palette.primary)
  root.style.setProperty('--primary-foreground', palette.foreground)
  root.style.setProperty('--primary-hover', palette.hover)
  root.style.setProperty('--primary-edge', palette.edge)
}
