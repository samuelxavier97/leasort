import type { Role } from '@/features/auth/types'

export const ROLE_LABELS: Record<Role, string> = {
  ADMIN: 'Administrador',
  PROSPECTOR: 'Prospector',
  GATE: 'Portaria',
  HOST: 'Anfitrião',
}

/** Tela principal de cada perfil (SPEC §16.1). */
export function landingPath(role: Role): string {
  switch (role) {
    case 'ADMIN':
    case 'PROSPECTOR':
      return '/dashboard'
    case 'GATE':
      return '/portaria'
    case 'HOST':
      return '/chegadas'
  }
}

export interface NavItem {
  to: string
  label: string
}

/** Menu por perfil (SPEC §16.1), só com as telas já existentes. */
export const NAV_ITEMS: Record<Role, NavItem[]> = {
  ADMIN: [
    { to: '/dashboard', label: 'Dashboard' },
    { to: '/leads', label: 'Leads' },
    { to: '/prospectores', label: 'Prospectores' },
    { to: '/visitas', label: 'Visitas' },
    { to: '/convites', label: 'Convites' },
    { to: '/chegadas', label: 'Chegadas' },
    { to: '/acessos', label: 'Acessos' },
    { to: '/exportacoes', label: 'Exportações' },
    { to: '/usuarios', label: 'Usuários' },
    { to: '/auditoria', label: 'Auditoria' },
  ],
  PROSPECTOR: [
    { to: '/dashboard', label: 'Dashboard' },
    { to: '/leads', label: 'Meus Leads' },
    { to: '/agenda', label: 'Agenda' },
    { to: '/convites', label: 'Convites' },
    { to: '/chegadas', label: 'Chegadas de hoje' },
    { to: '/historico', label: 'Histórico' },
    { to: '/perfil', label: 'Perfil' },
  ],
  GATE: [
    { to: '/portaria', label: 'Validar Convite' },
    { to: '/acessos', label: 'Acessos Recentes' },
  ],
  HOST: [{ to: '/chegadas', label: 'Chegadas de hoje' }],
}

/** Telas fora do menu, pelo caminho; as do menu usam o rótulo do perfil. */
const OTHER_SCREENS: [RegExp, string][] = [
  [/^\/leads\/importar$/, 'Importar Leads'],
  [/^\/leads\/[^/]+$/, 'Lead'],
  [/^\/visitas\/[^/]+$/, 'Visita'],
  [/^\/convites\/[^/]+$/, 'Convite'],
  [/^\/chegadas\/[^/]+\/ficha$/, 'Ficha da visita'],
  [/^\/trocar-senha$/, 'Trocar senha'],
]

/** Nome da tela para o título da aba (D-117). */
export function screenTitle(pathname: string, role: Role): string | null {
  const item = NAV_ITEMS[role].find((navItem) => navItem.to === pathname)
  return item?.label ?? OTHER_SCREENS.find(([pattern]) => pattern.test(pathname))?.[1] ?? null
}
