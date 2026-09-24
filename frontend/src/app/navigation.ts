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
    { to: '/usuarios', label: 'Usuários' },
  ],
  PROSPECTOR: [
    { to: '/dashboard', label: 'Dashboard' },
    { to: '/leads', label: 'Meus Leads' },
    { to: '/agenda', label: 'Agenda' },
    { to: '/convites', label: 'Convites' },
    { to: '/historico', label: 'Histórico' },
    { to: '/perfil', label: 'Perfil' },
  ],
  GATE: [{ to: '/portaria', label: 'Validar Convite' }],
  HOST: [{ to: '/chegadas', label: 'Chegadas de hoje' }],
}
