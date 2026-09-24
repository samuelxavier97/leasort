import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { toast } from 'sonner'
import { Pagination } from '@/components/Pagination'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { useMe } from '@/features/auth/useMe'
import { listProspectors } from '@/features/prospectors/api'
import { errorMessage } from '@/lib/errors'
import { assignLeads, leadsQueryKey, listLeads, type LeadFilters, type LeadStatus } from './api'
import { LeadFormDialog } from './LeadFormDialog'
import { LEAD_STATUS_LABELS } from './status'

const UNASSIGNED = '__unassigned'

export function LeadsPage() {
  const { data: me } = useMe()
  const isAdmin = me?.role === 'ADMIN'
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const [filters, setFilters] = useState<LeadFilters>({ page: 0 })
  const [search, setSearch] = useState('')
  const [selected, setSelected] = useState<Set<string>>(new Set())
  const [assignTo, setAssignTo] = useState('')
  const [creating, setCreating] = useState(false)

  const leads = useQuery({
    queryKey: [...leadsQueryKey, filters],
    queryFn: () => listLeads(filters),
    placeholderData: keepPreviousData,
  })
  const prospectors = useQuery({
    queryKey: ['prospectors', 'all'],
    queryFn: () => listProspectors(0, 100),
    enabled: isAdmin,
  })
  const activeProspectors = (prospectors.data?.content ?? []).filter((p) => p.active)

  const assign = useMutation({
    mutationFn: () => assignLeads([...selected], assignTo),
    onSuccess: ({ assigned }) => {
      toast.success(assigned === 1 ? '1 Lead atribuído.' : `${assigned} Leads atribuídos.`)
      setSelected(new Set())
      queryClient.invalidateQueries({ queryKey: leadsQueryKey })
    },
    onError: (error) => toast.error(errorMessage(error)),
  })

  const applySearch = (event: FormEvent) => {
    event.preventDefault()
    setFilters((current) => ({ ...current, page: 0, q: search.trim() || undefined }))
  }

  const toggle = (id: string) =>
    setSelected((current) => {
      const next = new Set(current)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })

  const content = leads.data?.content ?? []
  const allSelected = content.length > 0 && content.every((lead) => selected.has(lead.id))

  return (
    <section className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h1 className="text-2xl font-semibold">{isAdmin ? 'Leads' : 'Meus Leads'}</h1>
        {isAdmin && (
          <div className="flex gap-2">
            <Button variant="outline" asChild>
              <Link to="/leads/importar">Importar CSV</Link>
            </Button>
            <Button onClick={() => setCreating(true)}>Novo Lead</Button>
          </div>
        )}
      </div>

      <div className="flex flex-wrap items-end gap-3">
        <form onSubmit={applySearch} className="flex items-end gap-2">
          <div className="space-y-1">
            <Label htmlFor="lead-search">Buscar</Label>
            <Input
              id="lead-search"
              placeholder="Nome, e-mail ou telefone"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
            />
          </div>
          <Button type="submit" variant="outline">
            Buscar
          </Button>
        </form>
        <div className="space-y-1">
          <Label htmlFor="lead-status-filter">Status</Label>
          <select
            id="lead-status-filter"
            className="h-9 rounded-md border border-input bg-transparent px-3 text-sm"
            value={filters.status ?? ''}
            onChange={(event) =>
              setFilters((current) => ({ ...current, page: 0, status: event.target.value as LeadStatus | '' }))
            }
          >
            <option value="">Todos</option>
            {Object.entries(LEAD_STATUS_LABELS).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </div>
        {isAdmin && (
          <div className="space-y-1">
            <Label htmlFor="lead-prospector-filter">Prospector</Label>
            <select
              id="lead-prospector-filter"
              className="h-9 rounded-md border border-input bg-transparent px-3 text-sm"
              value={filters.unassigned ? UNASSIGNED : (filters.prospectorId ?? '')}
              onChange={(event) => {
                const value = event.target.value
                setFilters((current) => ({
                  ...current,
                  page: 0,
                  unassigned: value === UNASSIGNED || undefined,
                  prospectorId: value && value !== UNASSIGNED ? value : undefined,
                }))
              }}
            >
              <option value="">Todos</option>
              <option value={UNASSIGNED}>Não atribuídos</option>
              {(prospectors.data?.content ?? []).map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name}
                </option>
              ))}
            </select>
          </div>
        )}
      </div>

      {isAdmin && selected.size > 0 && (
        <div className="flex flex-wrap items-end gap-2 rounded-md border bg-background p-3" aria-label="Atribuição em lote">
          <span className="text-sm">{selected.size} selecionado(s)</span>
          <div className="space-y-1">
            <Label htmlFor="assign-prospector">Atribuir a</Label>
            <select
              id="assign-prospector"
              className="h-9 rounded-md border border-input bg-transparent px-3 text-sm"
              value={assignTo}
              onChange={(event) => setAssignTo(event.target.value)}
            >
              <option value="">Selecione o Prospector</option>
              {activeProspectors.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name} ({p.employeeCode})
                </option>
              ))}
            </select>
          </div>
          <Button disabled={!assignTo || assign.isPending} onClick={() => assign.mutate()}>
            Atribuir
          </Button>
        </div>
      )}

      {leads.isError && <p className="text-destructive">{errorMessage(leads.error)}</p>}
      {leads.data && (
        <div className="overflow-x-auto rounded-md border bg-background">
          <Table>
            <TableHeader>
              <TableRow>
                {isAdmin && (
                  <TableHead className="w-8">
                    <input
                      type="checkbox"
                      aria-label="Selecionar todos"
                      checked={allSelected}
                      onChange={() =>
                        setSelected(allSelected ? new Set() : new Set(content.map((lead) => lead.id)))
                      }
                    />
                  </TableHead>
                )}
                <TableHead>Nome</TableHead>
                <TableHead>CPF</TableHead>
                <TableHead>Telefone</TableHead>
                <TableHead>Status</TableHead>
                {isAdmin && <TableHead>Prospector</TableHead>}
              </TableRow>
            </TableHeader>
            <TableBody>
              {content.length === 0 && (
                <TableRow>
                  <TableCell colSpan={isAdmin ? 6 : 4} className="text-muted-foreground">
                    Nenhum Lead encontrado.
                  </TableCell>
                </TableRow>
              )}
              {content.map((lead) => (
                <TableRow key={lead.id}>
                  {isAdmin && (
                    <TableCell>
                      <input
                        type="checkbox"
                        aria-label={`Selecionar ${lead.name}`}
                        checked={selected.has(lead.id)}
                        onChange={() => toggle(lead.id)}
                      />
                    </TableCell>
                  )}
                  <TableCell>
                    <Link className="font-medium hover:underline" to={`/leads/${lead.id}`}>
                      {lead.name}
                    </Link>
                  </TableCell>
                  <TableCell>{lead.cpf ?? '—'}</TableCell>
                  <TableCell>{lead.phone ?? '—'}</TableCell>
                  <TableCell>
                    <Badge variant={lead.status === 'CANCELLED' ? 'outline' : 'secondary'}>
                      {LEAD_STATUS_LABELS[lead.status]}
                    </Badge>
                  </TableCell>
                  {isAdmin && <TableCell>{lead.prospector?.name ?? 'Não atribuído'}</TableCell>}
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}
      {leads.data && (
        <Pagination
          page={leads.data.page}
          totalPages={leads.data.totalPages}
          onChange={(page) => setFilters((current) => ({ ...current, page }))}
        />
      )}

      {isAdmin && me && (
        <LeadFormDialog
          open={creating}
          lead={null}
          role={me.role}
          prospectors={prospectors.data?.content}
          onClose={() => setCreating(false)}
          onSaved={(lead) => {
            setCreating(false)
            toast.success('Lead criado.')
            queryClient.invalidateQueries({ queryKey: leadsQueryKey })
            navigate(`/leads/${lead.id}`)
          }}
        />
      )}
    </section>
  )
}
