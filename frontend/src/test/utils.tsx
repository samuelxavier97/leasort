import { QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { vi } from 'vitest'
import { createQueryClient } from '@/app/queryClient'
import { routes } from '@/app/routes'
import type { Me, Role } from '@/features/auth/types'

export interface MockResponse {
  status?: number
  body?: unknown
  /** Corpo binário (ex.: PNG), enviado sem JSON. */
  raw?: { bytes: Uint8Array<ArrayBuffer>; type: string }
  /** Cabeçalhos extras da resposta (ex.: Content-Disposition de um download). */
  headers?: Record<string, string>
}

export type Handler = (method: string, url: string, body: unknown) => MockResponse | undefined

/** Substitui o fetch global; requisições sem handler falham o teste com 404 visível. */
export function mockFetch(handler: Handler) {
  const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = typeof input === 'string' ? input : input.toString()
    const method = init?.method ?? 'GET'
    const body = typeof init?.body === 'string' ? JSON.parse(init.body) : undefined
    const result = handler(method, url, body) ?? { status: 404, body: { code: 'NOT_MOCKED', detail: `${method} ${url}` } }
    const status = result.status ?? 200
    if (result.raw) {
      return new Response(result.raw.bytes, { status, headers: { 'Content-Type': result.raw.type, ...result.headers } })
    }
    return new Response(status === 204 || result.body === undefined ? null : JSON.stringify(result.body), {
      status,
      headers: { 'Content-Type': 'application/json' },
    })
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

export const unauthenticated: MockResponse = {
  status: 401,
  body: { status: 401, code: 'UNAUTHENTICATED', detail: 'Autenticação necessária.' },
}

export function me(role: Role, overrides: Partial<Me> = {}): Me {
  return { id: `id-${role}`, name: `Usuário ${role}`, email: `${role.toLowerCase()}@resort.local`, role, mustChangePassword: false, ...overrides }
}

/** PNG fictício (só a assinatura do formato) para respostas de QR. */
export function png(): MockResponse {
  return { raw: { bytes: new Uint8Array([137, 80, 78, 71, 13, 10, 26, 10]), type: 'image/png' } }
}

export function problem(status: number, code: string, detail = 'erro'): MockResponse {
  return { status, body: { status, code, detail } }
}

/** Renderiza o app inteiro (rotas reais) a partir de um caminho. */
export function renderApp(path: string) {
  const router = createMemoryRouter(routes, { initialEntries: [path] })
  const queryClient = createQueryClient()
  const view = render(
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  )
  return { router, queryClient, ...view }
}
