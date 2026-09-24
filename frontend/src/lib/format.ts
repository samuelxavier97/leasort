/** `1985-04-12` → `12/04/1985` (SPEC §16.8). */
export function formatDate(isoDate: string | null | undefined): string {
  if (!isoDate) {
    return '—'
  }
  const [year, month, day] = isoDate.slice(0, 10).split('-')
  return `${day}/${month}/${year}`
}
