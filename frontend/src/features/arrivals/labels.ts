/** Idade da ficha (D-096): "—" sem data de nascimento. */
export function formatAge(age: number | null): string {
  if (age === null) return '—'
  if (age === 0) return 'menos de 1 ano'
  return age === 1 ? '1 ano' : `${age} anos`
}

export const ARRIVALS_POLLING_MS = 30_000
