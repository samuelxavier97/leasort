import { describe, expect, it } from 'vitest'
import { DEFAULT_RESORT_NAME, readResortName } from './resort'

/** Documento como o que o Nginx entrega, com a meta tag já preenchida (escapada para HTML). */
function page(metaTag: string): Document {
  return new DOMParser().parseFromString(`<!doctype html><html><head>${metaTag}</head><body></body></html>`, 'text/html')
}

describe('readResortName', () => {
  it('é "Resort" sem a meta tag', () => {
    expect(readResortName(page(''))).toBe(DEFAULT_RESORT_NAME)
  })

  it('é "Resort" com a meta tag vazia ou em branco (sem RESORT_NAME no container)', () => {
    expect(readResortName(page('<meta name="resort-name" content="" />'))).toBe('Resort')
    expect(readResortName(page('<meta name="resort-name" content="   " />'))).toBe('Resort')
  })

  it('usa o nome da meta tag, sem espaços nas pontas', () => {
    expect(readResortName(page('<meta name="resort-name" content="  Resort Fictício das Águas  " />'))).toBe(
      'Resort Fictício das Águas',
    )
  })

  it('aspas e < escapados pelo container voltam como texto, sem virar marcação', () => {
    const doc = page('<meta name="resort-name" content="Resort &quot;Sol&quot; &lt;script&gt;alert(1)&lt;/script&gt; &amp; Mar" />')

    expect(readResortName(doc)).toBe('Resort "Sol" <script>alert(1)</script> & Mar')
    expect(doc.querySelectorAll('script')).toHaveLength(0)
  })
})
