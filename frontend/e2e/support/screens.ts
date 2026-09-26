import { expect, type Page } from '@playwright/test'

export interface CompanionInput {
  name: string
  relationship: string
  birthDate: string
  cpf: string
}

/** PROSPECTOR: abre o Lead pela carteira e agenda; devolve o código exibido no convite, sem hífen. */
export async function scheduleVisit(
  page: Page,
  leadName: string,
  visit: { date: string; companions?: CompanionInput[]; hostNotes?: string },
): Promise<string> {
  await page.getByRole('link', { name: 'Meus Leads' }).click()
  await page.getByRole('link', { name: leadName }).click()
  await page.getByRole('button', { name: 'Agendar visita' }).click()
  const dialog = page.getByRole('dialog', { name: 'Agendar visita' })
  await dialog.getByLabel('Data da visita').fill(visit.date)
  const companions = visit.companions ?? []
  if (companions.length > 0) {
    await dialog.getByRole('button', { name: 'Acompanhantes (opcional)' }).click()
    for (const [index, companion] of companions.entries()) {
      const number = index + 1
      await dialog.getByRole('button', { name: 'Adicionar acompanhante' }).click()
      await dialog.getByLabel(`Nome do acompanhante ${number}`).fill(companion.name)
      await dialog.getByLabel(`Parentesco do acompanhante ${number}`).selectOption({ label: companion.relationship })
      await dialog.getByLabel(`Nascimento do acompanhante ${number}`).fill(companion.birthDate)
      await dialog.getByLabel(`CPF do acompanhante ${number} (opcional)`).fill(companion.cpf)
    }
  }
  if (visit.hostNotes) {
    await dialog.getByRole('button', { name: 'Observações para o anfitrião (opcional)' }).click()
    await dialog.getByLabel('Observações para o anfitrião').fill(visit.hostNotes)
  }
  await dialog.getByRole('button', { name: 'Confirmar agendamento' }).click()
  return invitationCode(page)
}

/** Código na tela do convite (§16.4), no formato XXXXX-XXXXX; devolve os 10 caracteres. */
export async function invitationCode(page: Page): Promise<string> {
  await expect(page).toHaveURL(/\/convites\/[0-9a-f-]{36}$/)
  const code = page.getByTestId('invitation-code')
  await expect(code).toHaveText(/^[0-9A-Z]{5}-[0-9A-Z]{5}$/)
  await expect(page.getByRole('img', { name: 'QR Code do convite' })).toBeVisible()
  return (await code.innerText()).replace('-', '')
}

/**
 * Portaria: tenta a câmera, que o contexto não libera (sem permissão), vê o aviso e fica na digitação.
 * Com o problema de câmera conhecido, cada nova validação volta direto à digitação (Fase 6).
 */
export async function openTyping(page: Page): Promise<void> {
  await page.getByRole('button', { name: 'ESCANEAR QR CODE' }).click()
  await expect(page.getByRole('status').filter({ hasText: /câmera/i })).toBeVisible()
  await expect(page.getByLabel('Código do convite')).toBeVisible()
}

/** Portaria: digita o código no campo limpo e envia. */
export async function typeCode(page: Page, code: string): Promise<void> {
  const field = page.getByLabel('Código do convite')
  await expect(field).toHaveValue('')
  await field.fill(code)
  await expect(field).toHaveValue(`${code.slice(0, 5)}-${code.slice(5)}`)
  await page.getByRole('button', { name: 'VALIDAR' }).click()
}

/** Portaria: valida um código que deve ser negado com o motivo dado e volta para a validação. */
export async function expectDenied(page: Page, code: string, reason: string): Promise<void> {
  await typeCode(page, code)
  await expect(page.getByRole('heading', { name: 'ACESSO NEGADO' })).toBeVisible()
  await expect(page.getByText(reason, { exact: true })).toBeVisible()
  await page.getByRole('button', { name: 'NOVA VALIDAÇÃO' }).click()
}
