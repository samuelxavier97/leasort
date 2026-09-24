import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useParams } from 'react-router'
import { toast } from 'sonner'
import { ConfirmDialog } from '@/components/ConfirmDialog'
import { FormAlert } from '@/components/FormAlert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { useMe } from '@/features/auth/useMe'
import { errorMessage } from '@/lib/errors'
import { formatDate } from '@/lib/format'
import { changeLeadStatus, getLead, leadsQueryKey, type Lead } from './api'
import { LeadFormDialog } from './LeadFormDialog'
import { LEAD_ACTIONS, LEAD_STATUS_LABELS, leadActions, type LeadAction } from './status'

export function LeadDetailPage() {
  const { id = '' } = useParams()
  const { data: me } = useMe()
  const queryClient = useQueryClient()
  const [editing, setEditing] = useState(false)
  const [confirmDiscard, setConfirmDiscard] = useState(false)

  const lead = useQuery({ queryKey: [...leadsQueryKey, 'detail', id], queryFn: () => getLead(id) })

  const saved = (updated: Lead) => {
    queryClient.setQueryData([...leadsQueryKey, 'detail', id], updated)
    queryClient.invalidateQueries({ queryKey: leadsQueryKey, predicate: (query) => query.queryKey[1] !== 'detail' })
  }

  const statusChange = useMutation({
    mutationFn: (action: LeadAction) => changeLeadStatus(id, LEAD_ACTIONS[action].target),
    onSuccess: (updated) => {
      setConfirmDiscard(false)
      saved(updated)
      toast.success(`Status alterado para ${LEAD_STATUS_LABELS[updated.status]}.`)
    },
    onError: () => setConfirmDiscard(false),
  })

  if (lead.isPending) {
    return <p className="text-muted-foreground">Carregando...</p>
  }
  if (lead.isError || !me) {
    return (
      <section className="space-y-3">
        <p className="text-destructive">{errorMessage(lead.error)}</p>
        <Link className="text-sm underline" to="/leads">
          Voltar para a lista
        </Link>
      </section>
    )
  }

  const data = lead.data
  const actions = leadActions(data.status, me.role)

  return (
    <section className="max-w-3xl space-y-4">
      <Link className="text-sm text-muted-foreground hover:underline" to="/leads">
        ← Leads
      </Link>
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h1 className="text-2xl font-semibold">{data.name}</h1>
        <Badge variant={data.status === 'CANCELLED' ? 'outline' : 'secondary'}>{LEAD_STATUS_LABELS[data.status]}</Badge>
      </div>

      <FormAlert message={statusChange.isError ? errorMessage(statusChange.error) : null} />

      <div className="flex flex-wrap gap-2">
        {actions.map((action) => (
          <Button
            key={action}
            variant={action === 'DISCARD' ? 'outline' : 'default'}
            disabled={statusChange.isPending}
            onClick={() => (action === 'DISCARD' ? setConfirmDiscard(true) : statusChange.mutate(action))}
          >
            {LEAD_ACTIONS[action].label}
          </Button>
        ))}
        <Button variant="outline" onClick={() => setEditing(true)}>
          Editar
        </Button>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Dados do Lead</CardTitle>
        </CardHeader>
        <CardContent>
          <dl className="grid grid-cols-1 gap-x-6 gap-y-3 text-sm sm:grid-cols-[auto_1fr]">
            <dt className="text-muted-foreground">CPF</dt>
            <dd>{data.cpf ?? '—'}</dd>
            <dt className="text-muted-foreground">Telefone</dt>
            <dd>{data.phone ?? '—'}</dd>
            <dt className="text-muted-foreground">E-mail</dt>
            <dd>{data.email ?? '—'}</dd>
            <dt className="text-muted-foreground">Nascimento</dt>
            <dd>{formatDate(data.birthDate)}</dd>
            <dt className="text-muted-foreground">Prospector</dt>
            <dd>{data.prospector?.name ?? 'Não atribuído'}</dd>
            <dt className="text-muted-foreground">Notas internas</dt>
            <dd className="whitespace-pre-line">{data.notes ?? '—'}</dd>
          </dl>
        </CardContent>
      </Card>

      <LeadFormDialog
        open={editing}
        lead={data}
        role={me.role}
        onClose={() => setEditing(false)}
        onSaved={(updated) => {
          setEditing(false)
          saved(updated)
          toast.success('Lead atualizado.')
        }}
      />
      <ConfirmDialog
        open={confirmDiscard}
        title="Descartar Lead"
        description={`${data.name} ficará inativo. Só o administrador pode reativá-lo.`}
        confirmLabel="Descartar"
        pending={statusChange.isPending}
        onConfirm={() => statusChange.mutate('DISCARD')}
        onCancel={() => setConfirmDiscard(false)}
      />
    </section>
  )
}
