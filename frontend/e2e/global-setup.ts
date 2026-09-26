import { expect } from '@playwright/test'
import { Api } from './support/api.ts'
import { OPERATION_TIMEZONE } from './support/data.ts'
import { adminCredentials } from './support/fixtures.ts'

/**
 * Os fluxos agendam para "hoje" no fuso da operação: uma execução que atravessasse a meia-noite
 * veria o convite expirar, e o job noturno roda às 00:15. Entre 23:55 e 00:20 o E2E não roda.
 */
function refuseAroundMidnight(): void {
  const [hour, minute] = new Intl.DateTimeFormat('en-GB', {
    timeZone: OPERATION_TIMEZONE,
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  })
    .format(new Date())
    .split(':')
    .map(Number)
  const minutes = hour * 60 + minute
  if (minutes >= 23 * 60 + 55 || minutes < 20) {
    throw new Error(
      `E2E bloqueado entre 23:55 e 00:20 em ${OPERATION_TIMEZONE} (agora ${String(hour).padStart(2, '0')}:${String(minute).padStart(2, '0')}): ` +
        'os fluxos agendam para hoje e o job noturno roda às 00:15. Rode de novo depois das 00:20.',
    )
  }
}

/** Primeiro acesso do ADMIN inicial e conferência de que o banco começa vazio. */
export default async function globalSetup(): Promise<void> {
  refuseAroundMidnight()
  const { email, password } = adminCredentials()
  const bootstrapPassword = process.env.E2E_BOOTSTRAP_PASSWORD
  if (!bootstrapPassword) throw new Error('Rode o E2E por scripts/e2e.sh (falta E2E_BOOTSTRAP_PASSWORD).')

  const api = await Api.create()
  try {
    // O health responde UP antes de o ADMIN inicial existir (D-059): espera o login funcionar.
    // Cada falha conta no limite de 5 por e-mail e IP (§14), então no máximo 4 tentativas, a 1 s.
    let me: { mustChangePassword: boolean } | undefined
    await expect(async () => {
      const response = await api.login(email, bootstrapPassword)
      expect(response.status()).toBe(200)
      me = await response.json()
    }).toPass({ intervals: [1_000], timeout: 3_500 })
    expect(me?.mustChangePassword, 'o ADMIN inicial nasce com troca obrigatória (D-052)').toBe(true)
    await api.changePassword(bootstrapPassword, password)

    // Banco vazio de fato: só o ADMIN inicial, nenhum Lead, visita ou convite.
    for (const [path, expected] of [
      ['/api/users', 1],
      ['/api/leads', 0],
      ['/api/visits', 0],
      ['/api/invitations', 0],
    ] as const) {
      const body = await (await api.get(path)).json()
      expect(body.totalElements, `${path} no banco recém-criado`).toBe(expected)
    }
  } finally {
    await api.dispose()
  }
}
