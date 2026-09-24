import type { Relationship, VisitStatus } from './api'

export const VISIT_STATUS_LABELS: Record<VisitStatus, string> = {
  SCHEDULED: 'Agendada',
  COMPLETED: 'Realizada',
  CANCELLED: 'Cancelada',
  NO_SHOW: 'Não compareceu',
}

export const RELATIONSHIP_LABELS: Record<Relationship, string> = {
  SPOUSE: 'Cônjuge',
  CHILD: 'Filho(a)',
  FATHER: 'Pai',
  MOTHER: 'Mãe',
  SIBLING: 'Irmão(ã)',
  GRANDPARENT: 'Avô/Avó',
  GRANDCHILD: 'Neto(a)',
  FRIEND: 'Amigo(a)',
  OTHER: 'Outro',
}
