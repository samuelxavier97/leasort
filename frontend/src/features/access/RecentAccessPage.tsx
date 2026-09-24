import { useQuery } from '@tanstack/react-query'
import { Badge } from '@/components/ui/badge'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { useMe } from '@/features/auth/useMe'
import { OPERATION_TIMEZONE } from '@/lib/date'
import { errorMessage } from '@/lib/errors'
import { listRecentAccess, recentAccessQueryKey } from './api'
import { ACCESS_RESULT_LABELS, DENIAL_REASON_LABELS } from './labels'

const timeFormat = new Intl.DateTimeFormat('pt-BR', { timeZone: OPERATION_TIMEZONE, hour: '2-digit', minute: '2-digit' })

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
        <p className="text-muted-foreground">Carregando...</p>
      ) : recent.isError ? (
        <p className="text-destructive">{errorMessage(recent.error)}</p>
      ) : recent.data.length === 0 ? (
        <p className="text-muted-foreground">Nenhum acesso registrado hoje.</p>
      ) : (
        <div className="rounded-md border bg-background">
          <Table>
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
                  <TableCell>{timeFormat.format(new Date(access.createdAt))}</TableCell>
                  <TableCell>
                    <Badge variant={access.result === 'AUTHORIZED' ? 'secondary' : 'destructive'}>
                      {ACCESS_RESULT_LABELS[access.result]}
                    </Badge>
                  </TableCell>
                  <TableCell>{access.denialReason ? DENIAL_REASON_LABELS[access.denialReason] : '—'}</TableCell>
                  <TableCell>{access.leadName ?? '—'}</TableCell>
                  <TableCell>{access.gate}</TableCell>
                  <TableCell>{access.validatedBy}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}
    </section>
  )
}
