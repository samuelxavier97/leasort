export interface Series<K extends string> {
  key: K
  label: string
  /** Slot categórico em ordem fixa (D-100): a cor segue a série, nunca a posição. */
  color: string
}

export const VISIT_SERIES: Series<'scheduled' | 'completed' | 'noShow' | 'cancelled'>[] = [
  { key: 'scheduled', label: 'Agendadas', color: 'var(--series-1)' },
  { key: 'completed', label: 'Realizadas', color: 'var(--series-2)' },
  { key: 'noShow', label: 'No-show', color: 'var(--series-3)' },
  { key: 'cancelled', label: 'Cancelamentos', color: 'var(--series-4)' },
]

export const ACCESS_SERIES: Series<'authorized' | 'denied'>[] = [
  { key: 'authorized', label: 'Liberados', color: 'var(--series-1)' },
  { key: 'denied', label: 'Negados', color: 'var(--series-2)' },
]
