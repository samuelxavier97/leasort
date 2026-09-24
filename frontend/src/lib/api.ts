/** Erro da API no formato Problem Details, com o `code` estável do backend (D-033). */
export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly fieldErrors: { field: string; message: string }[]
  /** Corpo completo do Problem Details, para propriedades extras (ex.: relatório de importação). */
  readonly body: Record<string, unknown>

  constructor(
    status: number,
    code: string,
    detail: string,
    fieldErrors: { field: string; message: string }[] = [],
    body: Record<string, unknown> = {},
  ) {
    super(detail)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.fieldErrors = fieldErrors
    this.body = body
  }
}

type Method = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'

const UNSAFE_METHODS: Method[] = ['POST', 'PUT', 'PATCH', 'DELETE']

export function readCookie(name: string): string | undefined {
  const prefix = `${name}=`
  return document.cookie
    .split('; ')
    .find((cookie) => cookie.startsWith(prefix))
    ?.slice(prefix.length)
}

/**
 * Chamada à API na mesma origem. Em escritas envia o token CSRF lido do cookie `XSRF-TOKEN`
 * no header `X-XSRF-TOKEN` (D-054). Se o cookie ainda não existe, um GET inicial o obtém.
 */
export async function api<T>(path: string, options: { method?: Method; body?: unknown } = {}): Promise<T> {
  const method = options.method ?? 'GET'
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json'
  }
  return send<T>(path, method, headers, options.body === undefined ? undefined : JSON.stringify(options.body))
}

/** Envio de arquivo (multipart, campo `file`), com o mesmo CSRF das demais escritas. */
export function apiUpload<T>(path: string, file: File): Promise<T> {
  const form = new FormData()
  form.append('file', file)
  // Sem Content-Type: o navegador define o boundary do multipart.
  return send<T>(path, 'POST', { Accept: 'application/json' }, form)
}

async function send<T>(path: string, method: Method, headers: Record<string, string>, body?: BodyInit): Promise<T> {
  if (UNSAFE_METHODS.includes(method)) {
    if (!readCookie('XSRF-TOKEN')) {
      await fetch('/api/auth/me', { credentials: 'same-origin' })
    }
    const token = readCookie('XSRF-TOKEN')
    if (token) {
      headers['X-XSRF-TOKEN'] = decodeURIComponent(token)
    }
  }

  const response = await fetch(path, { method, headers, credentials: 'same-origin', body })

  if (!response.ok) {
    throw await toApiError(response)
  }
  if (response.status === 204) {
    return undefined as T
  }
  return (await response.json()) as T
}

async function toApiError(response: Response): Promise<ApiError> {
  try {
    const problem = await response.json()
    const errors: unknown[] = Array.isArray(problem.errors) ? problem.errors : []
    return new ApiError(
      response.status,
      typeof problem.code === 'string' ? problem.code : 'ERROR',
      typeof problem.detail === 'string' ? problem.detail : response.statusText,
      errors.filter((error): error is { field: string; message: string } =>
        typeof error === 'object' && error !== null && 'field' in error,
      ),
      problem,
    )
  } catch {
    return new ApiError(response.status, 'ERROR', response.statusText)
  }
}
