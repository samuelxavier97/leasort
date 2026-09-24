import { describe, expect, it } from 'vitest'
import { cleanAccessCode, formatAccessCode } from './accessCode'

// P2: cada regra da máscara separadamente.
describe('máscara do código de acesso', () => {
  it('converte para maiúsculas', () => {
    expect(cleanAccessCode('abcde')).toBe('ABCDE')
  })

  it('troca O por 0', () => {
    expect(cleanAccessCode('O0o')).toBe('000')
  })

  it('troca I e L por 1', () => {
    expect(cleanAccessCode('IiLl1')).toBe('11111')
  })

  it('remove caracteres fora do alfabeto (U, hífen, espaço, pontuação e acentos)', () => {
    expect(cleanAccessCode('A-B C_D.U/É:u9')).toBe('ABCD9')
  })

  it('limita a 10 caracteres', () => {
    expect(cleanAccessCode('0123456789ABCDEF')).toBe('0123456789')
  })

  it('exibe XXXXX-XXXXX, com o hífen só depois do quinto caractere', () => {
    expect(formatAccessCode('ABCDE')).toBe('ABCDE')
    expect(formatAccessCode('ABCDEF')).toBe('ABCDE-F')
    expect(formatAccessCode('ABCDEFGHJK')).toBe('ABCDE-FGHJK')
  })

  it('aceita de volta o valor exibido sem perder caracteres', () => {
    expect(cleanAccessCode(formatAccessCode('ABCDEFGHJK'))).toBe('ABCDEFGHJK')
  })
})
