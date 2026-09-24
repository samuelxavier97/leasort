import type { Page } from '@/lib/page'
import type { Lead } from '@/features/leads/api'

/** Lead fictício para testes de interface. */
export function fakeLead(overrides: Partial<Lead> = {}): Lead {
  return {
    id: 'lead-1',
    name: 'Lead Fictício',
    cpf: '***.456.789-**',
    phone: '11 90000-0000',
    email: 'lead@test.local',
    birthDate: '1985-04-12',
    notes: null,
    status: 'NEW',
    prospector: { id: 'p-1', name: 'Prospector Fictício' },
    createdAt: '2026-09-24T00:00:00Z',
    updatedAt: '2026-09-24T00:00:00Z',
    ...overrides,
  }
}

export function page<T>(content: T[]): Page<T> {
  return { content, page: 0, size: 20, totalElements: content.length, totalPages: 1 }
}
