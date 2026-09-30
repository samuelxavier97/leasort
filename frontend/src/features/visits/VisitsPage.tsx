import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router'
import { EmptyState, LoadError, PageLoading } from '@/components/PageState'
import { Pagination } from '@/components/Pagination'
import { Badge } from '@/components/ui/badge'
import { Label } from '@/components/ui/label'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { listProspectors } from '@/features/prospectors/api'
import { operationToday } from '@/lib/date'
import { formatDate } from '@/lib/format'
import { listVisits, visitsQueryKey, type VisitFilters, type VisitStatus } from './api'
import { VISIT_STATUS_LABELS } from './labels'

export type VisitsView = 'agenda' | 'history' | 'all'

const TITLES: Record<VisitsView, string> = { agenda: 'Agenda', history: 'Histórico', all: 'Visitas' }

const EMPTY: Record<VisitsView, string> = {
  agenda: 'Nenhuma visita agendada de hoje em diante.',
  history: 'Nenhuma visita no histórico.',
  all: 'Nenhuma visita encontrada.',
}

/**
 * Agenda: visitas SCHEDULED de hoje em diante, em ordem de data. Histórico: todas as demais, da mais
 * recente para a mais antiga (D-079). Visitas: lista do ADMIN, com filtros.
 */
export function VisitsPage({ view }: { view: VisitsView }) {
  const [page, setPage] = useState(0)
  const [status, setStatus] = useState<VisitStatus | ''>('')
  const [prospectorId, setProspectorId] = useState('')

  const filters: VisitFilters = { page }
  if (view === 'agenda') {
    Object.assign(filters, { status: 'SCHEDULED', from: operationToday(), order: 'asc' })
  } else if (view === 'history') {
    Object.assign(filters, { scope: 'history', order: 'desc' })
  } else {
    Object.assign(filters, { status, prospectorId: prospectorId || undefined, order: 'asc' })
  }

  const visits = useQuery({
    queryKey: [...visitsQueryKey, view, filters],
    queryFn: () => listVisits(filters),
    placeholderData: keepPreviousData,
  })
  const prospectors = useQuery({
    queryKey: ['prospectors', 'all'],
    queryFn: () => listProspectors(0, 100),
    enabled: view === 'all',
  })

  const showProspector = view !== 'agenda'
  const content = visits.data?.content ?? []

  return (
    <section className="space-y-4">
      <h1 className="text-2xl font-semibold">{TITLES[view]}</h1>

      {view === 'all' && (
        <div className="flex flex-wrap items-end gap-3">
          <div className="space-y-1">
            <Label htmlFor="visit-status-filter">Status</Label>
            <select
              id="visit-status-filter"
              className="h-9 rounded-md border border-input bg-background px-3 text-sm"
              value={status}
              onChange={(event) => {
                setStatus(event.target.value as VisitStatus | '')
                setPage(0)
              }}
            >
              <option value="">Todos</option>
              {Object.entries(VISIT_STATUS_LABELS).map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </select>
          </div>
          <div className="space-y-1">
            <Label htmlFor="visit-prospector-filter">Prospector</Label>
            <select
              id="visit-prospector-filter"
              className="h-9 rounded-md border border-input bg-background px-3 text-sm"
              value={prospectorId}
              onChange={(event) => {
                setProspectorId(event.target.value)
                setPage(0)
              }}
            >
              <option value="">Todos</option>
              {(prospectors.data?.content ?? []).map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name}
                </option>
              ))}
            </select>
          </div>
        </div>
      )}

      {visits.isPending && <PageLoading />}
      {visits.isError && <LoadError error={visits.error} onRetry={() => visits.refetch()} />}
      {visits.data && content.length === 0 && <EmptyState>{EMPTY[view]}</EmptyState>}
      {content.length > 0 && (
        <div className="overflow-x-auto rounded-md border bg-background">
          <Table className="table-cards">
            <TableHeader>
              <TableRow>
                <TableHead>Data</TableHead>
                <TableHead>Lead</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Acompanhantes</TableHead>
                {showProspector && <TableHead>Prospector</TableHead>}
              </TableRow>
            </TableHeader>
            <TableBody>
              {content.map((visit) => (
                <TableRow key={visit.id}>
                  <TableCell data-label="Data">
                    <Link className="font-medium hover:underline" to={`/visitas/${visit.id}`}>
                      {formatDate(visit.scheduledDate)}
                    </Link>
                  </TableCell>
                  <TableCell data-label="Lead">{visit.lead.name}</TableCell>
                  <TableCell data-label="Status">
                    <Badge variant={visit.status === 'SCHEDULED' ? 'secondary' : 'outline'}>
                      {VISIT_STATUS_LABELS[visit.status]}
                    </Badge>
                  </TableCell>
                  <TableCell data-label="Acompanhantes">{visit.companions.length}</TableCell>
                  {showProspector && <TableCell data-label="Prospector">{visit.prospector.name}</TableCell>}
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}
      {visits.data && <Pagination page={visits.data.page} totalPages={visits.data.totalPages} onChange={setPage} />}
    </section>
  )
}
