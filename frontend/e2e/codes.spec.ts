import { fakeCpf, operationDate } from './support/data.ts'
import { expect, test } from './support/fixtures.ts'
import { expectDenied, invitationCode, openTyping, scheduleVisit, typeCode } from './support/screens.ts'

/**
 * E2: o código que a tela mostra depois de remarcar ou reemitir é o único que a Portaria aceita;
 * os anteriores passam a ser negados como cancelados (§13, D-076).
 */
test('E2: remarcação e reemissão invalidam o código anterior e a Portaria só aceita o atual', async ({ world }) => {
  const prospector = await world.createUser('PROSPECTOR')
  const gate = await world.createUser('GATE')
  const leadName = `Lead E2E ${world.suffix}`
  await world.createLead({
    name: leadName,
    cpf: fakeCpf(),
    phone: '(11) 90000-0001',
    birthDate: '1975-01-20',
    prospectorId: prospector.prospectorId!,
  })

  const prospectorPage = await world.loggedIn('PROSPECTOR', prospector)
  const gatePage = await world.loggedIn('GATE', gate)
  await openTyping(gatePage)

  // Visita de amanhã: o código A ainda não vale hoje.
  const codeA = await scheduleVisit(prospectorPage, leadName, { date: operationDate(1) })
  await expectDenied(gatePage, codeA, 'Fora da data')

  // Remarcada para hoje: a tela abre o novo convite (código B), e A passa a ser cancelado.
  await prospectorPage.getByRole('link', { name: 'Ver visita' }).click()
  await prospectorPage.getByRole('button', { name: 'Remarcar' }).click()
  const reschedule = prospectorPage.getByRole('dialog', { name: 'Remarcar visita' })
  await reschedule.getByLabel('Nova data').fill(operationDate())
  await reschedule.getByRole('button', { name: 'Remarcar' }).click()
  await expect(reschedule).toBeHidden()
  const codeB = await invitationCode(prospectorPage)
  expect(codeB).not.toBe(codeA)
  await expectDenied(gatePage, codeA, 'Convite cancelado')

  // Reemitido: código C; B passa a ser cancelado e C é liberado.
  await prospectorPage.getByRole('button', { name: 'Reemitir código' }).click()
  await prospectorPage.getByRole('button', { name: 'Reemitir', exact: true }).click()
  await expect(prospectorPage.getByText('Novo código gerado. O código anterior deixou de valer.')).toBeVisible()
  await expect(prospectorPage.getByTestId('invitation-code')).not.toHaveText(`${codeB.slice(0, 5)}-${codeB.slice(5)}`)
  const codeC = await invitationCode(prospectorPage)
  expect([codeA, codeB]).not.toContain(codeC)
  await expectDenied(gatePage, codeB, 'Convite cancelado')

  await typeCode(gatePage, codeC)
  await expect(gatePage.getByRole('heading', { name: 'ACESSO LIBERADO' })).toBeVisible()
  await expect(gatePage.getByText(leadName, { exact: true })).toBeVisible()
})
