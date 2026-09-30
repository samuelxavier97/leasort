import { describe, expect, it } from 'vitest'
import { buttonVariants } from '@/components/ui/button'
import {
  applyBrand,
  brandName,
  brandPalette,
  contrastRatio,
  DEFAULT_BRAND_COLOR,
  DEMO_LABEL,
  documentTitle,
  EDGE_CONTRAST,
  isDemoInstance,
  PRODUCT_NAME,
  readBrand,
  TEXT_CONTRAST,
} from './brand'

/** Documento como o que o Nginx entrega, com as metas já preenchidas (o nome escapado para HTML). */
function page(metaTags: string): Document {
  return new DOMParser().parseFromString(`<!doctype html><html><head>${metaTags}</head><body></body></html>`, 'text/html')
}

const tag = (name: string, content: string) => `<meta name="${name}" content="${content}" />`

describe('readBrand — metas do tema (D-115)', () => {
  it('B1: sem metas, ou com as metas vazias, vale a identidade do Resortric', () => {
    const expected = { resortName: null, color: DEFAULT_BRAND_COLOR, logoUrl: null }
    expect(readBrand(page(''))).toEqual(expected)
    expect(
      readBrand(page(tag('resort-name', '  ') + tag('resort-brand-color', '') + tag('resort-brand-logo', ''))),
    ).toEqual(expected)
    expect(DEFAULT_BRAND_COLOR).toBe('#1f4e79')
  })

  it('B2: o nome vem sem espaços nas pontas, e o escapado pelo container volta como texto (D-087)', () => {
    expect(readBrand(page(tag('resort-name', '  Resort Fictício das Águas  '))).resortName).toBe('Resort Fictício das Águas')
    const doc = page(tag('resort-name', 'Resort &quot;Sol&quot; &lt;script&gt;alert(1)&lt;/script&gt; &amp; Mar'))
    expect(readBrand(doc).resortName).toBe('Resort "Sol" <script>alert(1)</script> & Mar')
    expect(doc.querySelectorAll('script')).toHaveLength(0)
  })

  it('B3: a cor aceita só #RRGGBB, em minúsculas; o resto vale a padrão', () => {
    expect(readBrand(page(tag('resort-brand-color', '#1E3A5F'))).color).toBe('#1e3a5f')
    expect(readBrand(page(tag('resort-brand-color', ' #f5d90a '))).color).toBe('#f5d90a')
    for (const invalid of ['#fff', 'red', 'rgb(0, 0, 0)', '#12345g', '#000000;x:y', '1e3a5f', '#1e3a5f00', 'url(x)']) {
      expect(readBrand(page(tag('resort-brand-color', invalid))).color, invalid).toBe(DEFAULT_BRAND_COLOR)
    }
  })

  it('B4: o logotipo aceita só /brand/logo.png', () => {
    expect(readBrand(page(tag('resort-brand-logo', '/brand/logo.png'))).logoUrl).toBe('/brand/logo.png')
    for (const invalid of [
      '/brand/logo.svg',
      'https://exemplo.invalid/logo.png',
      '//exemplo.invalid/brand/logo.png',
      'javascript:alert(1)',
      'data:image/png;base64,AAAA',
      '/brand/logo.png?x=1',
    ]) {
      expect(readBrand(page(tag('resort-brand-logo', invalid))).logoUrl, invalid).toBeNull()
    }
  })

  it('B5: nome e título da aba seguem a hierarquia (D-117)', () => {
    const none = readBrand(page(''))
    const named = readBrand(page(tag('resort-name', 'Resort Fictício das Águas')))
    expect(brandName(none)).toBe(PRODUCT_NAME)
    expect(PRODUCT_NAME).toBe('Resortric')
    expect(brandName(named)).toBe('Resort Fictício das Águas')
    expect(documentTitle('Leads', none)).toBe('Leads · Resortric')
    expect(documentTitle('Leads', named)).toBe('Leads · Resort Fictício das Águas')
    expect(documentTitle(null, named)).toBe('Resort Fictício das Águas')
  })
})

describe('isDemoInstance — instalação de demonstração (D-125)', () => {
  it('B13: só a meta resort-demo igual a "true" liga a etiqueta', () => {
    expect(DEMO_LABEL).toBe('Ambiente de demonstração')
    expect(isDemoInstance(page(tag('resort-demo', 'true')))).toBe(true)
    expect(isDemoInstance(page(tag('resort-demo', ' true ')))).toBe(true)
    expect(isDemoInstance(page(''))).toBe(false)
    for (const value of ['', 'false', 'TRUE', 'True', '1', 'yes', 'sim']) {
      expect(isDemoInstance(page(tag('resort-demo', value))), value).toBe(false)
    }
  })
})

describe('contraste da cor principal (D-118)', () => {
  it('B6: razão de contraste com valores de referência da WCAG', () => {
    expect(contrastRatio('#ffffff', '#000000')).toBeCloseTo(21, 5)
    expect(contrastRatio('#000000', '#ffffff')).toBeCloseTo(21, 5)
    expect(contrastRatio('#767676', '#ffffff')).toBeCloseTo(4.54, 2)
    expect(contrastRatio('#777777', '#ffffff')).toBeCloseTo(4.48, 2)
    expect(contrastRatio('#1f4e79', '#ffffff')).toBeCloseTo(8.66, 2)
  })

  it('B7: texto branco quando atinge 4,5:1; senão, preto', () => {
    const cases: [string, string][] = [
      ['#1e3a5f', '#ffffff'], // escura
      ['#1f4e79', '#ffffff'], // padrão do Resortric
      ['#767676', '#ffffff'], // limite: 4,54:1 com branco
      ['#777777', '#000000'], // limite: 4,48:1 com branco, 4,69:1 com preto
      ['#f5d90a', '#000000'], // clara
      ['#ffe8a3', '#000000'], // muito clara
      ['#e11d48', '#ffffff'], // vermelha
      ['#22c55e', '#000000'], // verde clara
    ]
    for (const [color, foreground] of cases) {
      const palette = brandPalette(color)
      expect(palette.foreground, color).toBe(foreground)
      expect(contrastRatio(palette.primary, palette.foreground), color).toBeGreaterThanOrEqual(TEXT_CONTRAST)
    }
  })

  it('B8: o hover escurece com texto branco e clareia com texto preto', () => {
    const dark = brandPalette('#1f4e79')
    expect(dark.hover).toBe('#1b456a')
    expect(contrastRatio(dark.hover, '#ffffff')).toBeGreaterThan(contrastRatio(dark.primary, '#ffffff'))
    const light = brandPalette('#f5d90a')
    expect(contrastRatio(light.hover, '#000000')).toBeGreaterThan(contrastRatio(light.primary, '#000000'))
  })

  it('B9: cor com menos de 3:1 contra o branco ganha a borda escurecida até 3:1; as demais usam a própria cor', () => {
    expect(brandPalette('#1f4e79').edge).toBe('#1f4e79')
    const light = brandPalette('#f5d90a')
    expect(contrastRatio('#f5d90a', '#ffffff')).toBeLessThan(EDGE_CONTRAST)
    expect(light.edge).not.toBe('#f5d90a')
    expect(contrastRatio(light.edge, '#ffffff')).toBeGreaterThanOrEqual(EDGE_CONTRAST)
    // Contra o fundo do item ativo do menu (bg-muted sobre o branco, ~#f5f5f5), a linha ainda aparece.
    expect(contrastRatio(light.edge, '#f5f5f5')).toBeGreaterThanOrEqual(2.5)
  })

  it('B10: varredura de 4.096 cores: texto, hover e borda sempre atingem os mínimos', () => {
    const levels = Array.from({ length: 16 }, (_, index) => (index * 17).toString(16).padStart(2, '0'))
    let checked = 0
    for (const r of levels) {
      for (const g of levels) {
        for (const b of levels) {
          const color = `#${r}${g}${b}`
          const palette = brandPalette(color)
          const base = contrastRatio(palette.primary, palette.foreground)
          expect(base, color).toBeGreaterThanOrEqual(TEXT_CONTRAST)
          expect(contrastRatio(palette.hover, palette.foreground), color).toBeGreaterThanOrEqual(base)
          expect(contrastRatio(palette.edge, '#ffffff'), color).toBeGreaterThanOrEqual(EDGE_CONTRAST)
          checked++
        }
      }
    }
    expect(checked).toBe(4096)
  })

  it('B11: applyBrand escreve as variáveis no :root por CSSOM, sem criar <style>', () => {
    const styles = document.querySelectorAll('style').length
    applyBrand(document.documentElement, { resortName: null, color: '#f5d90a', logoUrl: null })
    const root = document.documentElement.style
    expect(root.getPropertyValue('--primary')).toBe('#f5d90a')
    expect(root.getPropertyValue('--primary-foreground')).toBe('#000000')
    expect(root.getPropertyValue('--primary-hover')).toBe(brandPalette('#f5d90a').hover)
    expect(root.getPropertyValue('--primary-edge')).toBe(brandPalette('#f5d90a').edge)
    expect(document.querySelectorAll('style')).toHaveLength(styles)
  })

  it('B12: o botão principal usa o hover e a borda da regra, nunca a transparência /90', () => {
    const classes = buttonVariants().split(' ')
    expect(classes).toEqual(expect.arrayContaining(['bg-primary', 'text-primary-foreground', 'hover:bg-primary-hover', 'border-primary-edge']))
    expect(classes.filter((name) => name.includes('primary/'))).toEqual([])
  })
})
