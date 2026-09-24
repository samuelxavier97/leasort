import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { toast } from 'sonner'
import { ConfirmDialog } from '@/components/ConfirmDialog'
import { FormAlert } from '@/components/FormAlert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { useMe } from '@/features/auth/useMe'
import { leadsQueryKey } from '@/features/leads/api'
import { operationDate } from '@/lib/date'
import { errorMessage } from '@/lib/errors'
import { formatDate } from '@/lib/format'
import { cancelVisit, getVisit, visitsQueryKey, type Visit } from './api'
import { EditVisitDialog } from './EditVisitDialog'
import { RELATIONSHIP_LABELS, VISIT_STATUS_LABELS } from './labels'
import { RescheduleVisitDialog } from './RescheduleVisitDialog'

export function VisitDetailPage() {
  const { id = '' } = useParams()
  const { data: me } = useMe()
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const [editing, setEditing] = useState(false)
  const [rescheduling, setRescheduling] = useState(false)
  const [confirmCancel, setConfirmCancel] = useState(false)

  const detailKey = [...visitsQueryKey, 'detail', id]
  const visit = useQuery({ queryKey: detailKey, queryFn: () => getVisit(id) })

  /** Escritas mudam listas de visitas e o status do Lead. */
  const refreshRelated = () => {
    queryClient.invalidateQueries({ queryKey: visitsQueryKey, predicate: (query) => query.queryKey[1] !== 'detail' })
    queryClient.invalidateQueries({ queryKey: leadsQueryKey })
  }

  const cancel = useMutation({
    mutationFn: () => cancelVisit(id),
    onSuccess: (updated) => {
      setConfirmCancel(false)
      queryClient.setQueryData(detailKey, updated)
      refreshRelated()
      toast.success('Visita cancelada.')
    },
    onError: () => setConfirmCancel(false),
  })

  if (visit.isPending) {
    return <p className="text-muted-foreground">Carregando...</p>
  }
  if (visit.isError || !me) {
    return <p className="text-destructive">{errorMessage(visit.error)}</p>
  }

  const data = visit.data

  return (
    <section className="max-w-3xl space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <h1 className="text-2xl font-semibold">Visita de {formatDate(data.scheduledDate)}</h1>
          <p className="text-sm text-muted-foreground">
            Lead:{' '}
            {data.lead.accessible ? (
              <Link className="underline" to={`/leads/${data.lead.id}`}>
                {data.lead.name}
              </Link>
            ) : (
              // Responsável antigo: lê a visita, mas o Lead saiu da carteira dele (D-078).
              <span>{data.lead.name}</span>
            )}
          </p>
        </div>
        <Badge variant={data.status === 'SCHEDULED' ? 'secondary' : 'outline'}>{VISIT_STATUS_LABELS[data.status]}</Badge>
      </div>

      <FormAlert message={cancel.isError ? errorMessage(cancel.error) : null} />

      {data.status === 'COMPLETED' && (
        // A visita só fica COMPLETED pelo registro da entrada: a ficha existe (D-096).
        <Button variant="outline" asChild>
          <Link to={`/chegadas/${data.id}/ficha`}>Ver ficha</Link>
        </Button>
      )}
      {data.invitation ? (
        <Button variant="outline" asChild>
          <Link to={`/convites/${data.invitation.id}`}>Ver convite</Link>
        </Button>
      ) : (
        data.status === 'SCHEDULED' && (
          // Visita anterior aos convites (D-071): a remarcação gera o convite da nova visita.
          <p className="rounded-md border p-3 text-sm text-muted-foreground">
            Esta visita foi criada antes dos convites. Remarque-a para gerar um convite.
          </p>
        )
      )}

      {/* As ações dependem só de canEdit, que o backend já calcula como escrita permitida e visita SCHEDULED (D-078). */}
      {data.canEdit && (
        <div className="flex flex-wrap gap-2">
          <Button onClick={() => setEditing(true)}>Editar</Button>
          <Button variant="outline" onClick={() => setRescheduling(true)}>
            Remarcar
          </Button>
          <Button variant="outline" onClick={() => setConfirmCancel(true)} disabled={cancel.isPending}>
            Cancelar visita
          </Button>
        </div>
      )}

      <Card>
        <CardHeader>
          <CardTitle>Dados da visita</CardTitle>
        </CardHeader>
        <CardContent>
          <dl className="grid grid-cols-1 gap-x-6 gap-y-3 text-sm sm:grid-cols-[auto_1fr]">
            <dt className="text-muted-foreground">Data</dt>
            <dd>{formatDate(data.scheduledDate)}</dd>
            <dt className="text-muted-foreground">Prospector responsável</dt>
            <dd>{data.prospector.name}</dd>
            <dt className="text-muted-foreground">Observações internas</dt>
            <dd className="whitespace-pre-line">{data.notes ?? '—'}</dd>
            <dt className="text-muted-foreground">Observações para o anfitrião</dt>
            <dd className="whitespace-pre-line">{data.hostNotes ?? '—'}</dd>
            {data.cancelledAt && (
              <>
                <dt className="text-muted-foreground">Cancelada em</dt>
                <dd>{formatDate(operationDate(new Date(data.cancelledAt)))}</dd>
              </>
            )}
          </dl>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Acompanhantes</CardTitle>
        </CardHeader>
        <CardContent>
          {data.companions.length === 0 ? (
            <p className="text-sm text-muted-foreground">Nenhum acompanhante.</p>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Nome</TableHead>
                  <TableHead>Parentesco</TableHead>
                  <TableHead>Nascimento</TableHead>
                  <TableHead>CPF</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {data.companions.map((companion) => (
                  <TableRow key={companion.id}>
                    <TableCell>{companion.name}</TableCell>
                    <TableCell>{RELATIONSHIP_LABELS[companion.relationship]}</TableCell>
                    <TableCell>{formatDate(companion.birthDate)}</TableCell>
                    <TableCell>{companion.cpf ?? '—'}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>

      {data.canEdit && (
        <>
          <EditVisitDialog
            open={editing}
            visit={data}
            role={me.role}
            onClose={() => setEditing(false)}
            onSaved={(updated: Visit) => {
              setEditing(false)
              queryClient.setQueryData(detailKey, updated)
              refreshRelated()
              toast.success('Visita atualizada.')
            }}
          />
          <RescheduleVisitDialog
            open={rescheduling}
            visit={data}
            onClose={() => setRescheduling(false)}
            onRescheduled={(created) => {
              setRescheduling(false)
              queryClient.setQueryData([...visitsQueryKey, 'detail', created.id], created)
              queryClient.invalidateQueries({ queryKey: detailKey })
              refreshRelated()
              toast.success(`Visita remarcada para ${formatDate(created.scheduledDate)}.`)
              // O código novo precisa ser compartilhado de novo: abre o novo convite.
              navigate(created.invitation ? `/convites/${created.invitation.id}` : `/visitas/${created.id}`)
            }}
          />
        </>
      )}
      <ConfirmDialog
        open={confirmCancel}
        title="Cancelar visita"
        description={`A visita de ${formatDate(data.scheduledDate)} será cancelada e o Lead volta para "Contatado".`}
        confirmLabel="Confirmar cancelamento"
        pending={cancel.isPending}
        onConfirm={() => cancel.mutate()}
        onCancel={() => setConfirmCancel(false)}
      />
    </section>
  )
}
