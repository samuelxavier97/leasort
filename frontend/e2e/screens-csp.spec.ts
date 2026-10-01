import { adminCredentials, expect, test } from './support/fixtures.ts'
import { openFromMenu } from './support/screens.ts'

const ADMIN_MENU = [
  'Dashboard',
  'Leads',
  'Prospectores',
  'Visitas',
  'Convites',
  'Chegadas',
  'Acessos',
  'Exportações',
  'Usuários',
  'Auditoria',
]
const PROSPECTOR_MENU = ['Dashboard', 'Meus Leads', 'Agenda', 'Convites', 'Chegadas de hoje', 'Histórico', 'Perfil']

/**
 * E5: as telas de cada perfil, com gráficos (Recharts) e diálogo (Radix), abrem sem
 * nenhuma violação da Content-Security-Policy (D-107). Contra a pilha de produção por HTTPS, a CSP é a
 * do Nginx; o fixture reprova o teste em qualquer violação registrada no console.
 */
test('E5: as telas de cada perfil abrem sem violar a Content-Security-Policy', async ({ world }) => {
  const admin = await world.loggedIn('ADMIN', adminCredentials())
  for (const label of ADMIN_MENU) {
    await openFromMenu(admin, label)
    await expect(admin.getByRole('heading', { level: 1 })).toBeVisible()
  }
  // Diálogo do Radix, que injeta <style>. O aviso do sonner, que também injeta, aparece no E1 e no E3.
  await openFromMenu(admin, 'Usuários')
  await admin.getByRole('button', { name: 'Novo usuário' }).click()
  const form = admin.getByRole('dialog', { name: 'Novo usuário' })
  await expect(form).toBeVisible()
  await form.getByRole('button', { name: 'Cancelar' }).click()
  await expect(form).toBeHidden()

  const prospector = await world.loggedIn('PROSPECTOR', await world.createUser('PROSPECTOR'))
  for (const label of PROSPECTOR_MENU) {
    await prospector.getByRole('link', { name: label, exact: true }).click()
    await expect(prospector.getByRole('heading', { level: 1 })).toBeVisible()
  }

  const gate = await world.loggedIn('GATE', await world.createUser('GATE'))
  // A Portaria usa o viewport do celular: o menu fica no botão Menu (D-122).
  await gate.getByRole('button', { name: 'Menu' }).click()
  await gate.getByRole('menuitem', { name: 'Acessos Recentes' }).click()
  await expect(gate.getByRole('heading', { level: 1 })).toBeVisible()
})
