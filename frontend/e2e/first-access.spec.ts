import { request } from '@playwright/test'
import { BASE_URL } from './support/api.ts'
import { adminCredentials, expect, login, test } from './support/fixtures.ts'

/** E3: primeiro acesso com troca obrigatória e saída que invalida a sessão no servidor (§14, §24). */
test('E3: usuário criado pelo ADMIN troca a senha no primeiro acesso, entra e, ao sair, a sessão deixa de valer', async ({
  world,
}) => {
  const name = `Anfitrião E2E ${world.suffix}`
  const email = `primeiro-${world.suffix.toLowerCase()}@e2e.local`

  // O ADMIN cria o usuário pela tela; a senha temporária aparece uma vez.
  const adminPage = await world.loggedIn('ADMIN', adminCredentials())
  await adminPage.getByRole('link', { name: 'Usuários' }).click()
  await adminPage.getByRole('button', { name: 'Novo usuário' }).click()
  const form = adminPage.getByRole('dialog', { name: 'Novo usuário' })
  await form.getByLabel('Nome').fill(name)
  await form.getByLabel('E-mail').fill(email)
  await form.getByLabel('Perfil').selectOption({ label: 'Anfitrião' })
  await form.getByRole('button', { name: 'Criar usuário' }).click()
  const temporaryPassword = (await adminPage.getByTestId('temporary-password').innerText()).trim()
  expect(temporaryPassword).toMatch(/^\S{12}$/)
  await adminPage.getByRole('button', { name: 'Fechar' }).click()
  await expect(adminPage.getByTestId('temporary-password')).toBeHidden()

  // Primeiro acesso: só a troca de senha, depois a tela inicial do perfil.
  const context = await world.newContext('HOST')
  const page = await context.newPage()
  await login(page, email, temporaryPassword)
  await expect(page).toHaveURL('/trocar-senha')
  await page.goto('/chegadas')
  await expect(page).toHaveURL('/trocar-senha')
  const newPassword = `Nova-${world.suffix}-senha`
  await page.getByLabel('Senha atual').fill(temporaryPassword)
  await page.getByLabel('Nova senha', { exact: true }).fill(newPassword)
  await page.getByLabel('Confirme a nova senha').fill(newPassword)
  await page.getByRole('button', { name: 'Salvar nova senha' }).click()
  await expect(page).toHaveURL('/chegadas')
  await expect(page.getByRole('heading', { name: 'Chegadas de hoje' })).toBeVisible()

  // Sair: volta ao login, as telas pedem login de novo e o cookie antigo não vale mais no servidor.
  const signedIn = await context.storageState()
  await page.getByRole('button', { name }).click()
  await page.getByRole('menuitem', { name: 'Sair' }).click()
  await expect(page).toHaveURL('/login')
  await page.goto('/chegadas')
  await expect(page).toHaveURL('/login')

  const oldSession = await request.newContext({ baseURL: BASE_URL, storageState: signedIn })
  expect((await oldSession.get('/api/auth/me')).status()).toBe(401)
  await oldSession.dispose()

  // E a nova senha vale num login novo.
  await login(page, email, newPassword)
  await expect(page).toHaveURL('/chegadas')
})
