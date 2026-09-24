import { describe, expect, it } from 'vitest'
import type { Role } from '@/features/auth/types'
import type { LeadStatus } from './api'
import { leadActions } from './status'

describe('ações por status e perfil (§7.2, D-063)', () => {
  const cases: [LeadStatus, Role, string[]][] = [
    ['NEW', 'PROSPECTOR', ['CONTACT', 'DISCARD']],
    ['CONTACTED', 'PROSPECTOR', ['DISCARD']],
    ['VISIT_SCHEDULED', 'PROSPECTOR', ['DISCARD']],
    ['VISITED', 'PROSPECTOR', ['DISCARD']],
    ['CANCELLED', 'PROSPECTOR', []],
    ['NEW', 'ADMIN', ['CONTACT', 'DISCARD']],
    ['CANCELLED', 'ADMIN', ['REACTIVATE']],
  ]

  it.each(cases)('%s como %s', (status, role, expected) => {
    expect(leadActions(status, role)).toEqual(expected)
  })
})
