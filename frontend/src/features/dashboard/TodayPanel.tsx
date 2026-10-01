import { Link } from 'react-router'
import { Badge } from '@/components/ui/badge'
import { formatOperationTime } from '@/lib/date'
import type { TodayVisit } from './api'
import { Panel } from './StatCard'

function companionsText(visit: TodayVisit): string {
  if (visit.companionsCount === 0) {
    return 'sem acompanhantes'
  }
  if (visit.companionsPresent !== null) {
    return `${visit.companionsPresent} de ${visit.companionsCount} ${visit.companionsCount === 1 ? 'acompanhante presente' : 'acompanhantes presentes'}`
  }
  return visit.companionsCount === 1 ? '1 acompanhante' : `${visit.companionsCount} acompanhantes`
}

/**
 * "Hoje na sala de vendas" (D-128): as visitas de hoje, na ordem do backend (as que chegaram pela hora de
 * entrada, depois as agendadas pelo nome). A visita não tem horário marcado, então só a entrada tem hora.
 * "Chegou" usa a cor fixa de liberado (D-120), sempre com texto.
 */
export function TodayPanel({ visits, total }: { visits: TodayVisit[]; total: number }) {
  return (
    <Panel
      title="Hoje na sala de vendas"
      aside={
        <span className="flex items-baseline gap-3">
          <span>{total === 1 ? '1 visita' : `${total} visitas`}</span>
          <Link className="text-foreground underline" to="/chegadas">
            Ver chegadas
          </Link>
        </span>
      }
    >
      {visits.length === 0 ? (
        <p className="text-sm text-muted-foreground">Nenhuma visita para hoje.</p>
      ) : (
        <ul className="divide-y" aria-label="Visitas de hoje">
          {visits.map((visit) => (
            <li key={visit.visitId} className="flex flex-wrap items-center justify-between gap-x-3 gap-y-1 py-2.5">
              <div className="min-w-0">
                <p className="font-medium break-words">{visit.leadName}</p>
                <p className="text-sm text-muted-foreground">
                  {visit.prospectorName} · {companionsText(visit)}
                </p>
              </div>
              {visit.arrivedAt ? (
                <Badge variant="ok">Chegou às {formatOperationTime(visit.arrivedAt)}</Badge>
              ) : (
                <Badge variant="outline">Agendada</Badge>
              )}
            </li>
          ))}
        </ul>
      )}
      {total > visits.length && (
        <p className="text-sm text-muted-foreground">Mostrando as primeiras {visits.length} de {total}.</p>
      )}
    </Panel>
  )
}
