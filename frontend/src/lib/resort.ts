/** Padrão quando o container não define o nome (desenvolvimento, testes ou RESORT_NAME vazia). */
export const DEFAULT_RESORT_NAME = 'Resort'

/**
 * Nome do Resort exibido na imagem de compartilhamento do convite (D-087). Vem da meta tag
 * `resort-name` do `index.html`, que o container do Nginx preenche com `RESORT_NAME` ao subir, já
 * escapada para HTML; assim a mesma imagem serve a qualquer cliente. O nome real nunca entra num
 * arquivo do repositório.
 */
export function readResortName(doc: Document = document): string {
  return doc.querySelector<HTMLMetaElement>('meta[name="resort-name"]')?.content.trim() || DEFAULT_RESORT_NAME
}

export const RESORT_NAME = readResortName()
