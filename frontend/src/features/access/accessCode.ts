/** Alfabeto do código do convite: Crockford Base32, sem I, L, O e U (§13). */
const INVALID_CHARS = /[^0-9A-HJKMNP-TV-Z]/g

export const ACCESS_CODE_LENGTH = 10

/**
 * Código digitado na Portaria (§16.5): maiúsculas, O→0, I e L→1, remove o que está fora do
 * alfabeto e limita a 10 caracteres. É o valor enviado, sem hífen; o backend normaliza de novo (§12.1).
 */
export function cleanAccessCode(input: string): string {
  return input
    .toUpperCase()
    .replace(/O/g, '0')
    .replace(/[IL]/g, '1')
    .replace(INVALID_CHARS, '')
    .slice(0, ACCESS_CODE_LENGTH)
}

/** Exibição `XXXXX-XXXXX`; o hífen só aparece depois do quinto caractere. */
export function formatAccessCode(clean: string): string {
  return clean.length > 5 ? `${clean.slice(0, 5)}-${clean.slice(5)}` : clean
}
