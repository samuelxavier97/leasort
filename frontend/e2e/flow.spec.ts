import { ageOn, displayDate, fakeCpf, formatCpf, operationDate } from './support/data.ts'
import { adminCredentials, expect, test } from './support/fixtures.ts'
import { expectDenied, openTyping, scheduleVisit, typeCode } from './support/screens.ts'

/** E1: o fluxo mínimo da §23, com um contexto de navegador por perfil. */
test('E1: agenda com acompanhantes, a Portaria registra a entrada, o anfitrião vê a chegada e a ficha, e o código não vale de novo', async ({
  world,
}) => {
  const prospector = await world.createUser('PROSPECTOR')
  const gate = await world.createUser('GATE')
  const host = await world.createUser('HOST')
  const today = operationDate()

  // Lead e acompanhantes com CPF: a ficha precisa provar que nenhum deles aparece (§15).
  const lead = {
    name: `Lead E2E ${world.suffix}`,
    cpf: fakeCpf(),
    phone: '(11) 90000-0000',
    birthDate: '1980-05-10',
    prospectorId: prospector.prospectorId!,
  }
  await world.createLead(lead)
  const spouse = { name: `Cônjuge E2E ${world.suffix}`, relationship: 'Cônjuge', birthDate: '1982-03-15', cpf: fakeCpf() }
  const child = { name: `Filho E2E ${world.suffix}`, relationship: 'Filho(a)', birthDate: '2015-07-01', cpf: fakeCpf() }
  const hostNotes = `Prefere a área da piscina (${world.suffix}).`

  // 1-2. O PROSPECTOR agenda para hoje com dois acompanhantes e vê o convite.
  const prospectorPage = await world.loggedIn('PROSPECTOR', prospector)
  const code = await scheduleVisit(prospectorPage, lead.name, { date: today, companions: [spouse, child], hostNotes })

  // 3. O anfitrião já está na tela de chegadas, ainda sem esta chegada.
  const hostPage = await world.loggedIn('HOST', host)
  await expect(hostPage.getByRole('heading', { name: 'Chegadas de hoje' })).toBeVisible()
  const arrival = hostPage.getByRole('row').filter({ hasText: lead.name })
  await expect(arrival).toHaveCount(0)

  // 4. A Portaria, no celular, valida pela digitação e confirma sem o filho.
  const gatePage = await world.loggedIn('GATE', gate)
  await openTyping(gatePage)
  await typeCode(gatePage, code)
  await expect(gatePage.getByRole('heading', { name: 'ACESSO LIBERADO' })).toBeVisible()
  await expect(gatePage.getByText(lead.name, { exact: true })).toBeVisible()
  await expect(gatePage.getByText(displayDate(today), { exact: true })).toBeVisible()
  await expect(gatePage.getByText(prospector.name, { exact: true })).toBeVisible()
  const spouseBox = gatePage.getByRole('checkbox', { name: spouse.name })
  const childBox = gatePage.getByRole('checkbox', { name: child.name })
  await expect(spouseBox).toBeChecked()
  await expect(childBox).toBeChecked()
  await childBox.uncheck()
  await gatePage.getByRole('button', { name: 'CONFIRMAR ENTRADA' }).click()
  await expect(gatePage.getByText(`Entrada de ${lead.name} registrada.`)).toBeVisible()
  await expect(gatePage.getByLabel('Código do convite')).toHaveValue('')

  // 5. A chegada aparece para o anfitrião pelo polling de 30 s (§24: em até 30 s), sem recarregar.
  await expect(arrival).toHaveCount(1, { timeout: 35_000 })
  await expect(arrival.getByRole('cell').nth(2)).toHaveText('1')
  await expect(arrival.getByRole('cell').nth(3)).toHaveText(prospector.name)

  // 6. A ficha: dados da §16.6, presentes com idade, ausente sem idade e nenhum CPF.
  const sheetResponse = hostPage.waitForResponse((response) => /\/api\/visits\/[^/]+\/sheet$/.test(response.url()))
  await arrival.getByRole('link', { name: `Ficha de ${lead.name}` }).click()
  const sheetBody = await (await sheetResponse).text()
  await expect(hostPage.getByRole('heading', { name: `Ficha da visita — ${displayDate(today)}` })).toBeVisible()
  await expect(hostPage.getByText(lead.name, { exact: true })).toBeVisible()
  await expect(hostPage.getByText(`${ageOn(lead.birthDate)} anos`, { exact: true })).toBeVisible()
  await expect(hostPage.getByText(lead.phone, { exact: true })).toBeVisible()
  const present = hostPage.getByRole('region', { name: 'Acompanhantes presentes' }).getByRole('listitem')
  await expect(present).toHaveCount(1)
  await expect(present).toContainText(spouse.name)
  await expect(present).toContainText(`Cônjuge · ${ageOn(spouse.birthDate)} anos`)
  const absent = hostPage.getByRole('region', { name: 'Acompanhantes ausentes' }).getByRole('listitem')
  await expect(absent).toHaveCount(1)
  await expect(absent).toHaveText(`${child.name}Filho(a)`)
  await expect(hostPage.getByRole('region', { name: 'Observações para o anfitrião' })).toContainText(hostNotes)

  const html = await hostPage.content()
  for (const cpf of [lead.cpf, spouse.cpf, child.cpf]) {
    for (const shown of [cpf, formatCpf(cpf), cpf.slice(3, 9), formatCpf(cpf).slice(4, 11)]) {
      expect(html, 'CPF na página da ficha').not.toContain(shown)
      expect(sheetBody, 'CPF na resposta da ficha').not.toContain(shown)
    }
  }

  // 7. O mesmo código de novo: já utilizado.
  await expectDenied(gatePage, code, 'Convite já utilizado')

  // 8. O ADMIN vê a entrada e a negativa em Acessos.
  const adminPage = await world.loggedIn('ADMIN', adminCredentials())
  await adminPage.getByRole('link', { name: 'Acessos' }).click()
  const accesses = adminPage.getByRole('row').filter({ hasText: lead.name })
  await expect(accesses).toHaveCount(2)
  await expect(accesses.filter({ hasText: 'Liberado' })).toHaveCount(1)
  await expect(accesses.filter({ hasText: 'Convite já utilizado' })).toHaveCount(1)
})

