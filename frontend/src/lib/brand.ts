/** Nome do produto (D-116): o único lugar em que ele é escrito. */
export const PRODUCT_NAME = 'Resortric'

/** Cor principal padrão do Resortric (D-116, D-126), usada sem `BRAND_COLOR` ou com valor inválido. */
export const DEFAULT_BRAND_COLOR = '#1f4e79'

/** Dourado do Resortric (D-126): só decorativo, nunca texto (3,1:1 com branco), e nunca com tema de cliente. */
export const PRODUCT_GOLD = '#b08d57'

/** Texto sobre o dourado: o texto do sistema, 5,6:1 (D-128). */
export const PRODUCT_GOLD_FOREGROUND = '#1c1917'

/** Único endereço aceito para o logotipo: a cópia validada pelo Nginx na subida (D-115, D-119). */
export const BRAND_LOGO_URL = '/brand/logo.png'

/** Logotipo do Resortric (D-126): símbolo com a onda dourada e a palavra. */
export const PRODUCT_LOGO_URL = '/resortric.svg'

/** O mesmo logotipo com as duas ondas brancas, para quando há tema de cliente (D-126). */
export const PRODUCT_LOGO_MONO_URL = '/resortric-mono.svg'

const HEX_COLOR = /^#[0-9a-fA-F]{6}$/

export interface Brand {
  /** `RESORT_NAME`, ou null sem ele. */
  resortName: string | null
  /** Cor principal em `#rrggbb`, minúscula. */
  color: string
  /** `/brand/logo.png`, ou null sem logotipo. */
  logoUrl: string | null
  /** `BRAND_COLOR` informado (e válido); vazio não é tema de cliente, mesmo com a mesma cor (D-126). */
  customColor: boolean
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
  const customColor = HEX_COLOR.test(color)
  return {
    resortName: meta(doc, 'resort-name') || null,
    color: customColor ? color.toLowerCase() : DEFAULT_BRAND_COLOR,
    logoUrl: logo === BRAND_LOGO_URL ? BRAND_LOGO_URL : null,
    customColor,
  }
}

/**
 * Tema de cliente (D-126): `BRAND_COLOR` ou logotipo. Só `RESORT_NAME` não é tema: o nome aparece em
 * texto, e as cores e o dourado continuam os do Resortric.
 */
export function hasClientTheme(brand: Brand): boolean {
  return brand.customColor || brand.logoUrl !== null
}

/** Logotipo do Resortric para o tema atual: com tema de cliente, a versão sem dourado (D-126). */
export function productLogoUrl(brand: Brand): string {
  return hasClientTheme(brand) ? PRODUCT_LOGO_MONO_URL : PRODUCT_LOGO_URL
}

/** Favicon: só o símbolo, também sem dourado com tema de cliente (D-126). */
export function faviconUrl(brand: Brand): string {
  return hasClientTheme(brand) ? '/favicon-mono.svg' : '/favicon.svg'
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

/**
 * Tingimento pela cor principal (D-127), intensidade média: o fundo das telas é a base areia com 3,5% da
 * principal; as superfícies suaves, 7% (hover, cabeçalho de tabela) e 14% (item ativo, chips de ícone);
 * as bordas, o cinza com 12%. Cabeçalho do sistema e cartões continuam brancos.
 */
export const SAND = '#faf8f4'
const BORDER_GRAY = '#e7e5e4'
const PAGE_MIX = 0.035
const SOFT_MIX = 0.07
const SOFT_STRONG_MIX = 0.14
const BORDER_MIX = 0.12

export interface BrandPalette {
  primary: string
  /** Texto sobre a cor: branco se atingir 4,5:1; senão, preto puro (sempre atinge). */
  foreground: string
  /** Escurece com texto branco e clareia com texto preto: o contraste nunca cai. */
  hover: string
  /** Borda e indicador: a cor com 3:1 contra o branco e contra `softStrong`, ou escurecida até atingir. */
  edge: string
  /** Fundo das telas. */
  page: string
  /** Superfície suave: hover, cabeçalho de tabela (`--muted`, `--accent`, `--secondary`). */
  soft: string
  /** Superfície suave forte: item ativo do menu, chips de ícone. */
  softStrong: string
  /** Bordas e contorno dos campos. */
  border: string
}

/** A base (`#rrggbb`) com `amount` da cor principal. */
function tint(base: string, rgb: Rgb, amount: number): string {
  return toHex(mix(toRgb(base), rgb, amount))
}

export function brandPalette(color: string): BrandPalette {
  const rgb = toRgb(color)
  const whiteText = contrastRatio(color, '#ffffff') >= TEXT_CONTRAST
  const softStrong = tint(SAND, rgb, SOFT_STRONG_MIX)
  // O indicador do item ativo fica sobre o fundo 14% e encosta no cabeçalho branco: 3:1 contra os dois.
  const edgeContrast = (edge: Rgb) => Math.min(contrastRatio(toHex(edge), '#ffffff'), contrastRatio(toHex(edge), softStrong))
  let edge = rgb
  for (let step = 1; edgeContrast(edge) < EDGE_CONTRAST; step++) {
    edge = mix(rgb, BLACK, step / 20)
  }
  return {
    primary: color,
    foreground: whiteText ? '#ffffff' : '#000000',
    hover: toHex(mix(rgb, whiteText ? BLACK : WHITE, HOVER_MIX)),
    edge: toHex(edge),
    page: tint(SAND, rgb, PAGE_MIX),
    soft: tint(SAND, rgb, SOFT_MIX),
    softStrong,
    border: tint(BORDER_GRAY, rgb, BORDER_MIX),
  }
}

/**
 * Aplica a cor principal e o tingimento no `:root` por CSSOM, sem `<style>` inline (a CSP, D-107), e
 * troca o favicon. O dourado (`--brand-accent`) só existe sem tema de cliente; com ele, o detalhe vira a
 * cor principal e some na faixa (D-126).
 */
export function applyBrand(root: HTMLElement, brand: Brand): void {
  const palette = brandPalette(brand.color)
  const set = (name: string, value: string) => root.style.setProperty(name, value)
  set('--primary', palette.primary)
  set('--primary-foreground', palette.foreground)
  set('--primary-hover', palette.hover)
  set('--primary-edge', palette.edge)
  set('--page', palette.page)
  set('--soft-strong', palette.softStrong)
  for (const name of ['--muted', '--accent', '--secondary']) set(name, palette.soft)
  for (const name of ['--border', '--input']) set(name, palette.border)
  set('--brand-accent', hasClientTheme(brand) ? palette.primary : PRODUCT_GOLD)
  // Texto sobre o detalhe (o número do 1º lugar no ranking, D-128): quase preto sobre o dourado (5,6:1);
  // com tema de cliente, o da cor principal (D-118).
  set('--brand-accent-foreground', hasClientTheme(brand) ? palette.foreground : PRODUCT_GOLD_FOREGROUND)
  root.ownerDocument.querySelector<HTMLLinkElement>('link[rel="icon"]')?.setAttribute('href', faviconUrl(brand))
}
