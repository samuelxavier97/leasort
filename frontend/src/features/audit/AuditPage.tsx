import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { FormAlert } from '@/components/FormAlert'
import { Pagination } from '@/components/Pagination'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { listUsers, usersQueryKey } from '@/features/users/api'
import { OPERATION_TIMEZONE, operationToday } from '@/lib/date'
import { errorMessage } from '@/lib/errors'
import { auditQueryKey, searchAudit, type AuditFilters } from './api'
import { AUDIT_ACTION_LABELS, ENTITY_TYPE_LABELS, readableMetadata } from './labels'

const dateTime = new Intl.DateTimeFormat('pt-BR', {
  timeZone: OPERATION_TIMEZONE,
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
})

function minusDays(isoDate: string, days: number): string {
  const date = new Date(`${isoDate}T12:00:00Z`)
  date.setUTCDate(date.getUTCDate() - days)
  return date.toISOString().slice(0, 10)
}

/** Auditoria (§19, D-102): filtros de período, usuário, ação e entidade; da mais recente para a mais antiga. */
export function AuditPage() {
  const today = operationToday()
  const [draft, setDraft] = useState<AuditFilters>(() => ({
    from: minusDays(today, 29),
    to: today,
    userId: '',
    action: '',
    entityType: '',
    page: 0,
  }))
  const [filters, setFilters] = useState(draft)
  const invalidPeriod = Boolean(draft.from && draft.to && draft.from > draft.to) || Boolean(draft.from) !== Boolean(draft.to)

  function update(patch: Partial<AuditFilters>) {
    const next = { ...draft, ...patch, page: 0 }
    setDraft(next)
    const nextInvalid = Boolean(next.from && next.to && next.from > next.to) || Boolean(next.from) !== Boolean(next.to)
    if (!nextInvalid) setFilters(next)
  }

  const users = useQuery({ queryKey: [...usersQueryKey, 'all'], queryFn: () => listUsers(0, 100) })
  const audit = useQuery({ queryKey: [...auditQueryKey, filters], queryFn: () => searchAudit(filters) })
  const select = 'h-9 w-full rounded-md border border-input bg-transparent px-3 text-sm'

  return (
    <section className="space-y-4">
      <h1 className="text-2xl font-semibold">Auditoria</h1>

      <div role="group" aria-label="Filtros" className="grid gap-3 sm:grid-cols-3 lg:grid-cols-5">
        <div className="space-y-1">
          <Label htmlFor="audit-from">De</Label>
          <Input id="audit-from" type="date" value={draft.from} onChange={(e) => update({ from: e.target.value })} />
        </div>
        <div className="space-y-1">
          <Label htmlFor="audit-to">Até</Label>
          <Input id="audit-to" type="date" value={draft.to} onChange={(e) => update({ to: e.target.value })} />
        </div>
        <div className="space-y-1">
          <Label htmlFor="audit-user">Usuário</Label>
          <select id="audit-user" className={select} value={draft.userId} onChange={(e) => update({ userId: e.target.value })}>
            <option value="">Todos</option>
            {users.data?.content.map((user) => (
              <option key={user.id} value={user.id}>
                {user.name}
              </option>
            ))}
          </select>
        </div>
        <div className="space-y-1">
          <Label htmlFor="audit-action">Ação</Label>
          <select id="audit-action" className={select} value={draft.action} onChange={(e) => update({ action: e.target.value })}>
            <option value="">Todas</option>
            {Object.entries(AUDIT_ACTION_LABELS).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </div>
        <div className="space-y-1">
          <Label htmlFor="audit-entity">Entidade</Label>
          <select
            id="audit-entity"
            className={select}
            value={draft.entityType}
            onChange={(e) => update({ entityType: e.target.value })}
          >
            <option value="">Todas</option>
            {Object.entries(ENTITY_TYPE_LABELS).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </div>
      </div>

      <FormAlert
        message={
          invalidPeriod
            ? 'Informe as duas datas, com a inicial até a final.'
            : audit.isError
              ? errorMessage(audit.error)
              : null
        }
      />

      {audit.isPending ? (
        <p className="text-muted-foreground">Carregando...</p>
      ) : audit.data && audit.data.content.length === 0 ? (
        <p className="text-muted-foreground">Nenhum registro no período.</p>
      ) : audit.data ? (
        <>
          <div className="rounded-md border bg-background">
            <Table className="[&_td]:align-top [&_td]:whitespace-normal">
              <TableHeader>
                <TableRow>
                  <TableHead>Data e hora</TableHead>
                  <TableHead>Usuário</TableHead>
                  <TableHead>Ação</TableHead>
                  <TableHead>Entidade</TableHead>
                  <TableHead>Detalhes</TableHead>
                  <TableHead>IP</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {audit.data.content.map((entry) => {
                  const details = readableMetadata(entry.metadata)
                  return (
                    <TableRow key={entry.id}>
                      <TableCell className="whitespace-nowrap">{dateTime.format(new Date(entry.createdAt))}</TableCell>
                      <TableCell>{entry.userName}</TableCell>
                      <TableCell>{AUDIT_ACTION_LABELS[entry.action] ?? entry.action}</TableCell>
                      <TableCell>
                        {entry.entityType ? (ENTITY_TYPE_LABELS[entry.entityType] ?? entry.entityType) : '—'}
                        {entry.entityId && (
                          <span className="block font-mono text-xs text-muted-foreground" title={entry.entityId}>
                            {entry.entityId.slice(0, 8)}
                          </span>
                        )}
                      </TableCell>
                      <TableCell className="min-w-48 text-sm">
                        {details.length === 0 ? (
                          '—'
                        ) : (
                          <ul>
                            {details.map((line) => (
                              <li key={line}>{line}</li>
                            ))}
                          </ul>
                        )}
                      </TableCell>
                      <TableCell>{entry.ipAddress ?? '—'}</TableCell>
                    </TableRow>
                  )
                })}
              </TableBody>
            </Table>
          </div>
          <Pagination
            page={audit.data.page}
            totalPages={audit.data.totalPages}
            onChange={(page) => {
              setDraft({ ...draft, page })
              setFilters({ ...filters, page })
            }}
          />
        </>
      ) : null}
    </section>
  )
}
