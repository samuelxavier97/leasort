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
    { to: '/usuarios', label: 'Usuários' },
    { to: '/prospectores', label: 'Prospectores' },
  ],
  PROSPECTOR: [
    { to: '/dashboard', label: 'Dashboard' },
    { to: '/perfil', label: 'Perfil' },
  ],
  GATE: [{ to: '/portaria', label: 'Validar Convite' }],
  HOST: [{ to: '/chegadas', label: 'Chegadas de hoje' }],
}
