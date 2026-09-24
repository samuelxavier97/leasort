import { api, ApiError } from '@/lib/api'
import type { Me } from './types'

export const meQueryKey = ['me'] as const

/** Usuário da sessão, ou `null` quando não há sessão. Também entrega o cookie CSRF (D-054). */
export async function fetchMe(): Promise<Me | null> {
  try {
    return await api<Me>('/api/auth/me')
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      return null
    }
    throw error
  }
}

export function login(email: string, password: string): Promise<Me> {
  return api<Me>('/api/auth/login', { method: 'POST', body: { email, password } })
}

export function logout(): Promise<void> {
  return api<void>('/api/auth/logout', { method: 'POST' })
}

export function changePassword(currentPassword: string, newPassword: string): Promise<void> {
  return api<void>('/api/auth/change-password', { method: 'POST', body: { currentPassword, newPassword } })
}
