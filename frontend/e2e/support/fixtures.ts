import { test as base, devices, expect, type Browser, type BrowserContext, type Page } from '@playwright/test'
import { Api, BASE_URL, IGNORE_HTTPS_ERRORS } from './api.ts'
import { OPERATION_TIMEZONE, uniqueSuffix } from './data.ts'

export type Role = 'ADMIN' | 'PROSPECTOR' | 'GATE' | 'HOST'

export interface Person {
  id: string
  name: string
  email: string
  password: string
  prospectorId: string | null
}

export interface LeadInput {
  name: string
  cpf: string
  phone: string
  birthDate: string
  prospectorId: string
}

const LANDING: Record<Role, string> = {
  ADMIN: '/dashboard',
  PROSPECTOR: '/dashboard',
  GATE: '/portaria',
  HOST: '/chegadas',
}

export function adminCredentials(): { email: string; password: string } {
  const email = process.env.E2E_ADMIN_EMAIL
  const password = process.env.E2E_ADMIN_PASSWORD
  if (!email || !password) throw new Error('Rode o E2E por scripts/e2e.sh (faltam E2E_ADMIN_EMAIL e E2E_ADMIN_PASSWORD).')
  return { email, password }
}

/**
 * Dados de um teste: cada teste cria os próprios usuários e Leads (fictícios), então nenhum
 * depende de outro e o limite de validações por porteiro não interfere na repetição.
 */
export class World {
  readonly suffix = uniqueSuffix()
  private readonly contexts: BrowserContext[] = []
  /** Violações de CSP de todos os contextos do teste; qualquer uma reprova o teste (D-107). */
  readonly cspViolations: string[] = []
  private readonly apis: Api[] = []

  private readonly browser: Browser
  readonly admin: Api

  private constructor(browser: Browser, admin: Api) {
    this.browser = browser
    this.admin = admin
  }

  static async create(browser: Browser): Promise<World> {
    const admin = await Api.create()
    const { email, password } = adminCredentials()
    await admin.loginOk(email, password)
    return new World(browser, admin)
  }

  async dispose(): Promise<void> {
    for (const context of this.contexts) await context.close()
    expect(this.cspViolations, 'violações de Content-Security-Policy').toEqual([])
    for (const api of this.apis) await api.dispose()
    await this.admin.dispose()
  }

  /** Cria o usuário pelo ADMIN e troca a senha temporária pela API; o login do teste é pela tela. */
  async createUser(role: Exclude<Role, 'ADMIN'>, label: string = role): Promise<Person> {
    const tag = `${label}-${this.suffix}`.toLowerCase()
    const created = await this.admin.json<{ user: { id: string; name: string; prospectorId: string | null }; temporaryPassword: string }>(
      'POST',
      '/api/users',
      {
        name: `${label} E2E ${this.suffix}`,
        email: `${tag}@e2e.local`,
        role,
        employeeCode: role === 'PROSPECTOR' ? `E2E-${this.suffix}` : null,
        phone: null,
      },
    )
    const password = `E2e-${this.suffix}-senha`
    const own = await Api.create()
    await own.loginOk(`${tag}@e2e.local`, created.temporaryPassword)
    await own.changePassword(created.temporaryPassword, password)
    await own.dispose()
    return { id: created.user.id, name: created.user.name, email: `${tag}@e2e.local`, password, prospectorId: created.user.prospectorId }
  }

  /** Sessão da API como o usuário, para preparar dados que não são o foco do teste. */
  async apiAs(person: { email: string; password: string }): Promise<Api> {
    const api = await Api.create()
    this.apis.push(api)
    await api.loginOk(person.email, person.password)
    return api
  }

  async createLead(lead: LeadInput): Promise<{ id: string }> {
    return this.admin.json('POST', '/api/leads', { ...lead, email: null, notes: null })
  }

  /** Contexto de navegador só deste perfil; a Portaria usa viewport de celular. */
  async newContext(role: Role): Promise<BrowserContext> {
    const context = await this.browser.newContext({
      ...(role === 'GATE' ? devices['Pixel 7'] : {}),
      baseURL: BASE_URL,
      ignoreHTTPSErrors: IGNORE_HTTPS_ERRORS,
      locale: 'pt-BR',
      timezoneId: OPERATION_TIMEZONE,
    })
    watchCsp(context, this.cspViolations)
    this.contexts.push(context)
    return context
  }

  /** Login pela tela, num contexto novo; espera a tela inicial do perfil. */
  async loggedIn(role: Role, person: { email: string; password: string }): Promise<Page> {
    const page = await (await this.newContext(role)).newPage()
    await login(page, person.email, person.password)
    await expect(page).toHaveURL(LANDING[role])
    return page
  }
}

/** Registra as violações de CSP que o Chromium informa no console de qualquer página do contexto. */
export function watchCsp(context: BrowserContext, violations: string[]): void {
  context.on('console', (message) => {
    if (message.type() === 'error' && /Content Security Policy/i.test(message.text())) {
      violations.push(`${message.location().url}: ${message.text()}`)
    }
  })
}

export async function login(page: Page, email: string, password: string): Promise<void> {
  await page.goto('/login')
  await page.getByLabel('E-mail').fill(email)
  await page.getByLabel('Senha').fill(password)
  await page.getByRole('button', { name: 'Entrar' }).click()
}

export const test = base.extend<{ world: World }>({
  world: async ({ browser }, provide) => {
    const world = await World.create(browser)
    await provide(world)
    await world.dispose()
  },
})

export { expect }
