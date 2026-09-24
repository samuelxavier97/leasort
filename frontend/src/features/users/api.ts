import { api } from '@/lib/api'
import type { Page } from '@/lib/page'
import type { Role } from '@/features/auth/types'

export interface User {
  id: string
  name: string
  email: string
  role: Role
  active: boolean
  mustChangePassword: boolean
  createdAt: string
  prospectorId: string | null
  employeeCode: string | null
  phone: string | null
}

export interface CreateUserInput {
  name: string
  email: string
  role: Role
  employeeCode?: string
  phone?: string
}

export interface UpdateUserInput {
  name: string
  email: string
  role: Role
}

export const usersQueryKey = ['users'] as const

export function listUsers(page: number): Promise<Page<User>> {
  return api<Page<User>>(`/api/users?page=${page}`)
}

export function createUser(input: CreateUserInput): Promise<{ user: User; temporaryPassword: string }> {
  return api('/api/users', { method: 'POST', body: input })
}

export function updateUser(id: string, input: UpdateUserInput): Promise<User> {
  return api(`/api/users/${id}`, { method: 'PUT', body: input })
}

export function changeUserStatus(id: string, active: boolean): Promise<User> {
  return api(`/api/users/${id}/status`, { method: 'PATCH', body: { active } })
}

export function resetUserPassword(id: string): Promise<{ temporaryPassword: string }> {
  return api(`/api/users/${id}/reset-password`, { method: 'POST' })
}
