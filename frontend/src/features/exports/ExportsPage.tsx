import { useMutation, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { toast } from 'sonner'
import { FormAlert } from '@/components/FormAlert'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { ACCESS_RESULT_LABELS } from '@/features/access/labels'
import { LEAD_STATUS_LABELS } from '@/features/leads/status'
import { listProspectors, prospectorsQueryKey, type Prospector } from '@/features/prospectors/api'
import { VISIT_STATUS_LABELS } from '@/features/visits/labels'
import { errorMessage } from '@/lib/errors'
import { downloadExport, type ExportFile } from './api'

interface ExportBlock {
  file: ExportFile
  title: string
  description: string
  period: string
  statusLabel: string
  statuses: Record<string, string>
}

/** Os quatro arquivos da §18, com o status próprio de cada um (D-101). */
const BLOCKS: ExportBlock[] = [
  {
    file: 'leads',
    title: 'Leads',
    description: 'Nome, CPF, telefone, e-mail, nascimento, status, Prospector e data de criação.',
    period: 'Período pela data de criação do Lead. Prospector: o dono atual.',
    statusLabel: 'Status do Lead',
    statuses: LEAD_STATUS_LABELS,
  },
  {
    file: 'visits',
    title: 'Visitas',
    description: 'Lead, CPF, Prospector, data, status, motivo do cancelamento, acompanhantes e entrada.',
    period: 'Período pela data da visita. Prospector: quem agendou.',
    statusLabel: 'Status da visita',
    statuses: VISIT_STATUS_LABELS,
  },
  {
    file: 'companions',
    title: 'Acompanhantes',
    description: 'Visita, Lead, nome, CPF, nascimento, parentesco e presença na entrada.',
    period: 'Período pela data da visita. Prospector: quem agendou.',
    statusLabel: 'Status da visita',
    statuses: VISIT_STATUS_LABELS,
  },
  {
    file: 'access',
    title: 'Acessos',
    description: 'Data e hora, resultado, motivo, Lead, porteiro, portaria e acompanhantes presentes.',
    period: 'Período pela data da tentativa. Prospector: quem agendou a visita do convite.',
    statusLabel: 'Resultado',
    statuses: ACCESS_RESULT_LABELS,
  },
]

/** Exportações (§18, D-101): um bloco por arquivo, com filtros opcionais e o download do CSV. */
export function ExportsPage() {
  const prospectors = useQuery({ queryKey: [...prospectorsQueryKey, 'all'], queryFn: () => listProspectors(0, 100) })
  return (
    <section className="space-y-4">
      <div>
        <h1 className="text-2xl font-semibold">Exportações</h1>
        <p className="text-sm text-muted-foreground">
          Arquivos CSV para o Excel em português. Sem período, o arquivo traz todo o histórico. Os arquivos trazem CPF
          completo: guarde-os com cuidado.
        </p>
      </div>
      <div className="grid gap-4 lg:grid-cols-2">
        {BLOCKS.map((block) => (
          <ExportCard key={block.file} block={block} prospectors={prospectors.data?.content ?? []} />
        ))}
      </div>
    </section>
  )
}

function ExportCard({ block, prospectors }: { block: ExportBlock; prospectors: Prospector[] }) {
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [status, setStatus] = useState('')
  const [prospectorId, setProspectorId] = useState('')
  const id = (name: string) => `${block.file}-${name}`
  const onlyOneDate = Boolean(from) !== Boolean(to)
  const inverted = Boolean(from && to && from > to)
  const periodError = inverted
    ? 'A data inicial não pode ser posterior à final.'
    : onlyOneDate
      ? 'Informe as duas datas do período, ou nenhuma.'
      : null

  const download = useMutation({
    mutationFn: () =>
      downloadExport(block.file, {
        from: from || undefined,
        to: to || undefined,
        status: status || undefined,
        prospectorId: prospectorId || undefined,
      }),
    onSuccess: (name) => toast.success(`Arquivo ${name} baixado.`),
  })

  return (
    <Card>
      <CardContent className="space-y-3">
        <section aria-labelledby={id('title')} className="space-y-3">
          <div>
            <h2 id={id('title')} className="text-lg font-semibold">
              {block.title}
            </h2>
            <p className="text-sm text-muted-foreground">{block.description}</p>
            <p className="text-xs text-muted-foreground">{block.period}</p>
          </div>
          <div className="grid gap-3 sm:grid-cols-2">
            <div className="space-y-1">
              <Label htmlFor={id('from')}>De</Label>
              <Input id={id('from')} type="date" value={from} onChange={(e) => setFrom(e.target.value)} />
            </div>
            <div className="space-y-1">
              <Label htmlFor={id('to')}>Até</Label>
              <Input id={id('to')} type="date" value={to} onChange={(e) => setTo(e.target.value)} />
            </div>
            <div className="space-y-1">
              <Label htmlFor={id('status')}>{block.statusLabel}</Label>
              <select
                id={id('status')}
                className="h-9 w-full rounded-md border border-input bg-transparent px-3 text-sm"
                value={status}
                onChange={(e) => setStatus(e.target.value)}
              >
                <option value="">Todos</option>
                {Object.entries(block.statuses).map(([value, label]) => (
                  <option key={value} value={value}>
                    {label}
                  </option>
                ))}
              </select>
            </div>
            <div className="space-y-1">
              <Label htmlFor={id('prospector')}>Prospector</Label>
              <select
                id={id('prospector')}
                className="h-9 w-full rounded-md border border-input bg-transparent px-3 text-sm"
                value={prospectorId}
                onChange={(e) => setProspectorId(e.target.value)}
              >
                <option value="">Todos</option>
                {prospectors.map((prospector) => (
                  <option key={prospector.id} value={prospector.id}>
                    {prospector.name} ({prospector.employeeCode})
                  </option>
                ))}
              </select>
            </div>
          </div>
          <FormAlert message={periodError ?? (download.isError ? errorMessage(download.error) : null)} />
          <Button onClick={() => download.mutate()} disabled={Boolean(periodError) || download.isPending}>
            {download.isPending ? 'Gerando...' : 'Baixar CSV'}
          </Button>
        </section>
      </CardContent>
    </Card>
  )
}
