import { api } from '@/lib/api'
import type { Page } from '@/lib/page'

export interface Prospector {
  id: string
  userId: string
  name: string
  email: string
  active: boolean
  employeeCode: string
  phone: string | null
}

export const prospectorsQueryKey = ['prospectors'] as const

export function listProspectors(page: number, size = 20): Promise<Page<Prospector>> {
  return api<Page<Prospector>>(`/api/prospectors?page=${page}&size=${size}`)
}

export function updateProspector(id: string, input: { employeeCode: string; phone: string | null }): Promise<Prospector> {
  return api(`/api/prospectors/${id}`, { method: 'PUT', body: input })
}
