import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router'
import { EmptyState, LoadError, PageLoading } from '@/components/PageState'
import { Pagination } from '@/components/Pagination'
import { Badge } from '@/components/ui/badge'
import { Label } from '@/components/ui/label'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { formatDate } from '@/lib/format'
import { invitationsQueryKey, listInvitations, type InvitationFilters, type InvitationStatus } from './api'
import { INVITATION_STATUS_LABELS } from './labels'

/** Convites (§16.1): abre nos ativos, em ordem de data da visita. */
export function InvitationsPage() {
  const [page, setPage] = useState(0)
  const [status, setStatus] = useState<InvitationStatus | ''>('ACTIVE')
  const filters: InvitationFilters = { page, status, order: 'asc' }

  const invitations = useQuery({
    queryKey: [...invitationsQueryKey, 'list', filters],
    queryFn: () => listInvitations(filters),
    placeholderData: keepPreviousData,
  })
  const content = invitations.data?.content ?? []

  return (
    <section className="space-y-4">
      <h1 className="text-2xl font-semibold">Convites</h1>
      <div className="space-y-1">
        <Label htmlFor="invitation-status-filter">Status</Label>
        <select
          id="invitation-status-filter"
          className="h-9 rounded-md border border-input bg-transparent px-3 text-sm"
          value={status}
          onChange={(event) => {
            setStatus(event.target.value as InvitationStatus | '')
            setPage(0)
          }}
        >
          <option value="">Todos</option>
          {Object.entries(INVITATION_STATUS_LABELS).map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </select>
      </div>

      {invitations.isPending && <PageLoading />}
      {invitations.isError && <LoadError error={invitations.error} onRetry={() => invitations.refetch()} />}
      {invitations.data && content.length === 0 && <EmptyState>Nenhum convite encontrado.</EmptyState>}
      {content.length > 0 && (
        <div className="overflow-x-auto rounded-md border bg-background">
          <Table className="table-cards">
            <TableHeader>
              <TableRow>
                <TableHead>Data da visita</TableHead>
                <TableHead>Lead</TableHead>
                <TableHead>Código</TableHead>
                <TableHead>Status</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {content.map((invitation) => (
                <TableRow key={invitation.id}>
                  <TableCell data-label="Data da visita">
                    <Link className="font-medium hover:underline" to={`/convites/${invitation.id}`}>
                      {formatDate(invitation.visit.scheduledDate)}
                    </Link>
                  </TableCell>
                  <TableCell data-label="Lead">{invitation.lead.name}</TableCell>
                  <TableCell data-label="Código" className="font-mono">{invitation.formattedCode}</TableCell>
                  <TableCell data-label="Status">
                    <Badge variant={invitation.status === 'ACTIVE' ? 'secondary' : 'outline'}>
                      {INVITATION_STATUS_LABELS[invitation.status]}
                    </Badge>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}
      {invitations.data && (
        <Pagination page={invitations.data.page} totalPages={invitations.data.totalPages} onChange={setPage} />
      )}
    </section>
  )
}
