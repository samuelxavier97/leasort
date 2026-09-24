/** CPF fictício gerado na hora, com dígitos verificadores válidos. Nenhum CPF fixo nos testes. */
export function fakeCpf(): string {
  let base = ''
  do {
    base = Array.from({ length: 9 }, () => Math.floor(Math.random() * 10)).join('')
  } while (/^(\d)\1{8}$/.test(base))
  const check = (digits: string, length: number) => {
    let sum = 0
    for (let i = 0; i < length; i++) sum += Number(digits[i]) * (length + 1 - i)
    const remainder = (sum * 10) % 11
    return remainder === 10 ? 0 : remainder
  }
  const first = check(base, 9)
  return `${base}${first}${check(`${base}${first}`, 10)}`
}
