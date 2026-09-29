import { useQuery } from '@tanstack/react-query'
import { EmptyState, LoadError, PageLoading } from '@/components/PageState'
import { Badge } from '@/components/ui/badge'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { useMe } from '@/features/auth/useMe'
import { formatOperationTime } from '@/lib/date'
import { listRecentAccess, recentAccessQueryKey } from './api'
import { ACCESS_RESULT_LABELS, DENIAL_REASON_LABELS } from './labels'

/** Acessos de hoje, do mais recente ao mais antigo (D-092). GATE: "Acessos Recentes"; ADMIN: "Acessos". */
export function RecentAccessPage() {
  const { data: me } = useMe()
  const recent = useQuery({ queryKey: recentAccessQueryKey, queryFn: listRecentAccess })
  const title = me?.role === 'ADMIN' ? 'Acessos' : 'Acessos Recentes'

  return (
    <section className="space-y-4">
      <div>
        <h1 className="text-2xl font-semibold">{title}</h1>
        <p className="text-sm text-muted-foreground">Validações de hoje, da mais recente para a mais antiga.</p>
      </div>
      {recent.isPending ? (
        <PageLoading />
      ) : recent.isError ? (
        <LoadError error={recent.error} onRetry={() => recent.refetch()} />
      ) : recent.data.length === 0 ? (
        <EmptyState>Nenhum acesso registrado hoje.</EmptyState>
      ) : (
        <div className="rounded-md border bg-background">
          <Table className="table-cards">
            <TableHeader>
              <TableRow>
                <TableHead>Hora</TableHead>
                <TableHead>Resultado</TableHead>
                <TableHead>Motivo</TableHead>
                <TableHead>Lead</TableHead>
                <TableHead>Portaria</TableHead>
                <TableHead>Validado por</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {recent.data.map((access) => (
                <TableRow key={access.id}>
                  <TableCell data-label="Hora">{formatOperationTime(access.createdAt)}</TableCell>
                  <TableCell data-label="Resultado">
                    {/* As mesmas cores fixas do resultado da Portaria (D-120). */}
                    <Badge variant={access.result === 'AUTHORIZED' ? 'ok' : 'denied'}>
                      {ACCESS_RESULT_LABELS[access.result]}
                    </Badge>
                  </TableCell>
                  <TableCell data-label="Motivo">{access.denialReason ? DENIAL_REASON_LABELS[access.denialReason] : '—'}</TableCell>
                  <TableCell data-label="Lead">{access.leadName ?? '—'}</TableCell>
                  <TableCell data-label="Portaria">{access.gate}</TableCell>
                  <TableCell data-label="Validado por">{access.validatedBy}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}
    </section>
  )
}
