import { afterEach, describe, expect, it, vi } from 'vitest'

// P8: a constante é lida na carga do módulo, por isso cada caso reimporta o módulo.
async function loadResortName() {
  vi.resetModules()
  return (await import('./resort')).RESORT_NAME
}

describe('RESORT_NAME', () => {
  afterEach(() => {
    vi.unstubAllEnvs()
  })

  it('é "Resort" sem VITE_RESORT_NAME', async () => {
    vi.stubEnv('VITE_RESORT_NAME', undefined)
    expect(await loadResortName()).toBe('Resort')
  })

  it('é "Resort" com VITE_RESORT_NAME em branco', async () => {
    vi.stubEnv('VITE_RESORT_NAME', '   ')
    expect(await loadResortName()).toBe('Resort')
  })

  it('usa VITE_RESORT_NAME quando definida, sem espaços nas pontas', async () => {
    vi.stubEnv('VITE_RESORT_NAME', '  Resort Fictício das Águas  ')
    expect(await loadResortName()).toBe('Resort Fictício das Águas')
  })
})
