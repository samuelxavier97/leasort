/** Utilitários de CPF para a interface. A validação de verdade é do backend (RN14). */

export function onlyDigits(value: string): string {
  return value.replace(/\D/g, '')
}

/** Máscara progressiva de digitação: `12345678901` → `123.456.789-01`. */
export function formatCpfInput(value: string): string {
  const digits = onlyDigits(value).slice(0, 11)
  const parts = [digits.slice(0, 3), digits.slice(3, 6), digits.slice(6, 9)].filter(Boolean)
  const base = parts.join('.')
  return digits.length > 9 ? `${base}-${digits.slice(9)}` : base
}

export function isValidCpf(value: string): boolean {
  const digits = onlyDigits(value)
  if (digits.length !== 11 || /^(\d)\1{10}$/.test(digits)) {
    return false
  }
  const check = (length: number) => {
    let sum = 0
    for (let i = 0; i < length; i++) {
      sum += Number(digits[i]) * (length + 1 - i)
    }
    const remainder = (sum * 10) % 11
    return remainder === 10 ? 0 : remainder
  }
  return check(9) === Number(digits[9]) && check(10) === Number(digits[10])
}
