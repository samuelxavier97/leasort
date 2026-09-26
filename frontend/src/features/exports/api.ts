import { toApiError } from '@/lib/api'

export type ExportFile = 'leads' | 'visits' | 'companions' | 'access'

export interface ExportFilters {
  from?: string
  to?: string
  status?: string
  prospectorId?: string
}

/** Só os filtros preenchidos vão na URL (D-101). */
export function exportUrl(file: ExportFile, filters: ExportFilters): string {
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(filters)) {
    if (value) params.set(key, value)
  }
  const text = params.toString()
  return `/api/exports/${file}${text ? `?${text}` : ''}`
}

/** `attachment; filename="leads-2026-09-25.csv"` → `leads-2026-09-25.csv`. */
export function fileNameFrom(disposition: string | null, fallback: string): string {
  const match = disposition?.match(/filename\*?=(?:UTF-8'')?"?([^";]+)"?/i)
  return match ? decodeURIComponent(match[1]) : fallback
}

/**
 * Baixa o CSV com a sessão atual e salva com o nome do Content-Disposition. O arquivo traz CPF completo:
 * a URL do Blob é revogada logo depois do clique.
 */
export async function downloadExport(file: ExportFile, filters: ExportFilters): Promise<string> {
  const response = await fetch(exportUrl(file, filters), { credentials: 'same-origin', headers: { Accept: 'text/csv' } })
  if (!response.ok) {
    throw await toApiError(response)
  }
  const blob = await response.blob()
  const name = fileNameFrom(response.headers.get('Content-Disposition'), `${file}.csv`)
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = name
  document.body.append(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
  return name
}
