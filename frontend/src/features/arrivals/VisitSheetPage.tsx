import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router'
import { Button } from '@/components/ui/button'
import { RELATIONSHIP_LABELS } from '@/features/visits/labels'
import { formatOperationTime } from '@/lib/date'
import { errorMessage } from '@/lib/errors'
import { formatDate } from '@/lib/format'
import { arrivalsQueryKey, getVisitSheet } from './api'
import { formatAge } from './labels'

/**
 * Ficha da visita (§16.6, D-096). Impressão por `@media print` em uma página A4 (D-025): o cabeçalho do
 * sistema e os botões ficam de fora; nenhuma biblioteca de PDF.
 */
export function VisitSheetPage() {
  const { visitId = '' } = useParams()
  const sheet = useQuery({ queryKey: [...arrivalsQueryKey, 'sheet', visitId], queryFn: () => getVisitSheet(visitId) })

  if (sheet.isPending) {
    return <p className="text-muted-foreground">Carregando...</p>
  }
  if (sheet.isError) {
    return (
      <section className="max-w-2xl space-y-3">
        <p className="text-destructive">{errorMessage(sheet.error)}</p>
        <Button variant="outline" asChild>
          <Link to="/chegadas">Voltar às chegadas</Link>
        </Button>
      </section>
    )
  }

  const data = sheet.data
  return (
    <article className="visit-sheet mx-auto max-w-2xl space-y-5 rounded-md border bg-background p-6 print:max-w-none print:space-y-3 print:rounded-none print:border-0 print:p-0">
      <h1 className="text-2xl font-semibold print:text-xl">Ficha da visita — {formatDate(data.scheduledDate)}</h1>

      <dl className="grid grid-cols-[auto_1fr] gap-x-6 gap-y-1">
        <dt className="text-muted-foreground">Lead</dt>
        <dd className="min-w-0 break-words font-semibold">{data.lead.name}</dd>
        <dt className="text-muted-foreground">Idade</dt>
        <dd>{formatAge(data.lead.age)}</dd>
        <dt className="text-muted-foreground">Telefone</dt>
        <dd>{data.lead.phone ?? '—'}</dd>
        <dt className="text-muted-foreground">Prospector</dt>
        <dd className="min-w-0 break-words">{data.prospectorName}</dd>
        <dt className="text-muted-foreground">Horário de entrada</dt>
        <dd>{formatOperationTime(data.entryAt)}</dd>
      </dl>

      <section aria-labelledby="present-title" className="space-y-1">
        <h2 id="present-title" className="font-semibold">
          Acompanhantes presentes
        </h2>
        {data.presentCompanions.length === 0 ? (
          <p className="text-muted-foreground">Nenhum</p>
        ) : (
          <ul className="divide-y rounded-md border print:rounded-none">
            {data.presentCompanions.map((companion, index) => (
              <li key={index} className="flex flex-wrap justify-between gap-x-4 px-3 py-1.5 print:py-1">
                <span className="min-w-0 break-words font-medium">{companion.name}</span>
                <span className="text-muted-foreground">
                  {RELATIONSHIP_LABELS[companion.relationship]} · {formatAge(companion.age)}
                </span>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section aria-labelledby="absent-title" className="space-y-1">
        <h2 id="absent-title" className="font-semibold">
          Acompanhantes ausentes
        </h2>
        {data.absentCompanions.length === 0 ? (
          <p className="text-muted-foreground">Nenhum</p>
        ) : (
          <ul className="divide-y rounded-md border print:rounded-none">
            {data.absentCompanions.map((companion, index) => (
              <li key={index} className="flex flex-wrap justify-between gap-x-4 px-3 py-1.5 print:py-1">
                <span className="min-w-0 break-words">{companion.name}</span>
                <span className="text-muted-foreground">{RELATIONSHIP_LABELS[companion.relationship]}</span>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section aria-labelledby="notes-title" className="space-y-1">
        <h2 id="notes-title" className="font-semibold">
          Observações para o anfitrião
        </h2>
        <p className="whitespace-pre-wrap break-words">{data.hostNotes ?? 'Sem observações'}</p>
      </section>

      <div className="flex flex-wrap gap-2 print:hidden" data-testid="sheet-actions">
        <Button onClick={() => window.print()}>Imprimir</Button>
        <Button variant="outline" asChild>
          <Link to="/chegadas">Voltar às chegadas</Link>
        </Button>
      </div>
    </article>
  )
}
