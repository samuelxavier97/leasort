import { expect, request, type APIRequestContext, type APIResponse } from '@playwright/test'

export const BASE_URL = 'http://localhost:4173'

/**
 * Cliente da API para preparar dados (usuários e Leads) com a sessão e o CSRF reais (D-054):
 * o cookie XSRF-TOKEN vem do GET inicial e é trocado no login, então o header é lido a cada escrita.
 */
export class Api {
  private readonly context: APIRequestContext

  private constructor(context: APIRequestContext) {
    this.context = context
  }

  static async create(): Promise<Api> {
    const api = new Api(await request.newContext({ baseURL: BASE_URL }))
    await api.context.get('/api/auth/me')
    return api
  }

  async dispose(): Promise<void> {
    await this.context.dispose()
  }

  private async xsrf(): Promise<string> {
    const { cookies } = await this.context.storageState()
    const token = cookies.find((cookie) => cookie.name === 'XSRF-TOKEN')?.value
    if (!token) throw new Error('Sem cookie XSRF-TOKEN.')
    return token
  }

  async get(path: string): Promise<APIResponse> {
    return this.context.get(path)
  }

  async write(method: 'POST' | 'PUT' | 'PATCH', path: string, data: unknown): Promise<APIResponse> {
    return this.context.fetch(path, { method, data, headers: { 'X-XSRF-TOKEN': await this.xsrf() } })
  }

  async login(email: string, password: string): Promise<APIResponse> {
    return this.write('POST', '/api/auth/login', { email, password })
  }

  /** Login esperado com sucesso; devolve o corpo de /me. */
  async loginOk(email: string, password: string): Promise<{ mustChangePassword: boolean }> {
    const response = await this.login(email, password)
    expect(response.status(), `login de ${email}`).toBe(200)
    return response.json()
  }

  async changePassword(currentPassword: string, newPassword: string): Promise<void> {
    const response = await this.write('POST', '/api/auth/change-password', { currentPassword, newPassword })
    expect(response.ok(), 'troca de senha').toBe(true)
  }

  async json<T>(method: 'POST' | 'PUT' | 'PATCH', path: string, data: unknown): Promise<T> {
    const response = await this.write(method, path, data)
    expect(response.ok(), `${method} ${path}: ${response.status()} ${await response.text()}`).toBe(true)
    return response.json() as Promise<T>
  }
}
