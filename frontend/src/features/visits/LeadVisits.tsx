import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router'
import { Pagination } from '@/components/Pagination'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { errorMessage } from '@/lib/errors'
import { formatDate } from '@/lib/format'
import { listVisits, visitsQueryKey } from './api'
import { VISIT_STATUS_LABELS } from './labels'

/** Histórico de visitas na tela do Lead (§16.2), da mais recente para a mais antiga. */
export function LeadVisits({ leadId }: { leadId: string }) {
  const [page, setPage] = useState(0)
  const visits = useQuery({
    queryKey: [...visitsQueryKey, 'lead', leadId, page],
    queryFn: () => listVisits({ page, leadId, order: 'desc' }),
    placeholderData: keepPreviousData,
  })

  return (
    <Card>
      <CardHeader>
        <CardTitle>Histórico de visitas</CardTitle>
      </CardHeader>
      <CardContent className="space-y-3">
        {visits.isPending && <p className="text-sm text-muted-foreground">Carregando...</p>}
        {visits.isError && <p className="text-sm text-destructive">{errorMessage(visits.error)}</p>}
        {visits.data && visits.data.content.length === 0 && (
          <p className="text-sm text-muted-foreground">Nenhuma visita.</p>
        )}
        {visits.data && visits.data.content.length > 0 && (
          <Table aria-label="Histórico de visitas">
            <TableHeader>
              <TableRow>
                <TableHead>Data</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Prospector</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {visits.data.content.map((visit) => (
                <TableRow key={visit.id}>
                  <TableCell>
                    <Link className="font-medium hover:underline" to={`/visitas/${visit.id}`}>
                      {formatDate(visit.scheduledDate)}
                    </Link>
                  </TableCell>
                  <TableCell>
                    <Badge variant={visit.status === 'SCHEDULED' ? 'secondary' : 'outline'}>
                      {VISIT_STATUS_LABELS[visit.status]}
                    </Badge>
                  </TableCell>
                  <TableCell>{visit.prospector.name}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
        {visits.data && <Pagination page={visits.data.page} totalPages={visits.data.totalPages} onChange={setPage} />}
      </CardContent>
    </Card>
  )
}
