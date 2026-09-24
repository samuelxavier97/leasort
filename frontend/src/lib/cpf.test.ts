import { describe, expect, it } from 'vitest'
import { fakeCpf } from '@/test/fakeCpf'
import { formatCpfInput, isValidCpf, onlyDigits } from './cpf'

describe('cpf', () => {
  it('aplica a máscara progressiva durante a digitação', () => {
    expect(formatCpfInput('123')).toBe('123')
    expect(formatCpfInput('1234')).toBe('123.4')
    expect(formatCpfInput('1234567')).toBe('123.456.7')
    expect(formatCpfInput('1234567890')).toBe('123.456.789-0')
    expect(formatCpfInput('123.456.789-01999')).toBe('123.456.789-01')
    expect(formatCpfInput('abc')).toBe('')
  })

  it.each(Array.from({ length: 20 }, (_, i) => i))('valida os dígitos verificadores (amostra %i)', () => {
    const cpf = fakeCpf()
    expect(isValidCpf(cpf)).toBe(true)
    expect(isValidCpf(formatCpfInput(cpf))).toBe(true)
    const wrong = cpf.slice(0, 10) + ((Number(cpf[10]) + 1) % 10)
    expect(isValidCpf(wrong)).toBe(false)
  })

  it('rejeita sequências repetidas e tamanhos errados', () => {
    expect(isValidCpf('11111111111')).toBe(false)
    expect(isValidCpf('123')).toBe(false)
    expect(isValidCpf('')).toBe(false)
  })

  it('extrai só os dígitos', () => {
    expect(onlyDigits('123.456.789-01')).toBe('12345678901')
  })
})
