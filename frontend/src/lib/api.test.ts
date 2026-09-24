import { afterEach, describe, expect, it } from 'vitest'
import { mockFetch, problem } from '@/test/utils'
import { api, ApiError } from './api'

afterEach(() => {
  document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
})

function headersOf(call: unknown[]): Record<string, string> {
  return ((call[1] as RequestInit).headers ?? {}) as Record<string, string>
}

describe('api: CSRF', () => {
  it.each(['POST', 'PUT', 'PATCH', 'DELETE'] as const)('envia X-XSRF-TOKEN lido do cookie em %s', async (method) => {
    document.cookie = 'XSRF-TOKEN=token-123; path=/'
    const fetchMock = mockFetch(() => ({ status: 204 }))

    await api('/api/qualquer', { method, body: {} })

    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(headersOf(fetchMock.mock.calls[0])['X-XSRF-TOKEN']).toBe('token-123')
  })

  it('não envia o token em GET', async () => {
    document.cookie = 'XSRF-TOKEN=token-123; path=/'
    const fetchMock = mockFetch(() => ({ body: {} }))

    await api('/api/qualquer')

    expect(headersOf(fetchMock.mock.calls[0])['X-XSRF-TOKEN']).toBeUndefined()
  })

  it('sem cookie, faz um GET inicial para obtê-lo antes da escrita', async () => {
    const fetchMock = mockFetch((method, url) => {
      if (method === 'GET' && url === '/api/auth/me') {
        document.cookie = 'XSRF-TOKEN=novo-token; path=/'
        return { status: 401, body: {} }
      }
      return { status: 204 }
    })

    await api('/api/auth/login', { method: 'POST', body: {} })

    expect(fetchMock.mock.calls.map((call) => call[0])).toEqual(['/api/auth/me', '/api/auth/login'])
    expect(headersOf(fetchMock.mock.calls[1])['X-XSRF-TOKEN']).toBe('novo-token')
  })
})

describe('api: Problem Details', () => {
  it('converte a resposta de erro em ApiError com o code do backend', async () => {
    mockFetch(() => ({
      status: 409,
      body: { status: 409, code: 'EMAIL_ALREADY_EXISTS', detail: 'Já existe.', errors: [{ field: 'email', message: 'x' }] },
    }))

    const error = await api('/api/users').catch((caught: unknown) => caught)

    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({ status: 409, code: 'EMAIL_ALREADY_EXISTS', message: 'Já existe.' })
    expect((error as ApiError).fieldErrors).toEqual([{ field: 'email', message: 'x' }])
  })

  it('usa code ERROR quando a resposta não é Problem Details', async () => {
    mockFetch(() => ({ status: 502 }))

    const error = await api('/api/users').catch((caught: unknown) => caught)

    expect(error).toMatchObject({ status: 502, code: 'ERROR' })
  })

  it('retorna undefined em 204', async () => {
    document.cookie = 'XSRF-TOKEN=t; path=/'
    mockFetch(() => problem(204, ''))

    await expect(api('/api/auth/logout', { method: 'POST' })).resolves.toBeUndefined()
  })
})
