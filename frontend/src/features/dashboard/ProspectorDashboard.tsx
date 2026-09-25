import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router'
import { errorMessage } from '@/lib/errors'
import { formatDate } from '@/lib/format'
import { dashboardQueryKey, getProspectorSummary, getVisitsByDay } from './api'
import { DayChart } from './DayChart'
import { VISIT_SERIES } from './series'
import { StatCard, StatGrid } from './StatCard'

/** Dashboard do PROSPECTOR (§16.7): sem filtros; sempre os próprios números, nos 30 dias até hoje (D-098). */
export function ProspectorDashboard() {
  const summary = useQuery({ queryKey: [...dashboardQueryKey, 'summary', 'mine'], queryFn: getProspectorSummary })
  const visits = useQuery({ queryKey: [...dashboardQueryKey, 'visits', 'mine'], queryFn: () => getVisitsByDay({}) })

  if (!summary.data) {
    return (
      <section className="space-y-5">
        <h1 className="text-2xl font-semibold">Dashboard</h1>
        {summary.isError ? (
          <p className="text-destructive">{errorMessage(summary.error)}</p>
        ) : (
          <p className="text-muted-foreground">Carregando...</p>
        )}
      </section>
    )
  }
  const data = summary.data

  return (
    <section className="space-y-5">
      <div>
        <h1 className="text-2xl font-semibold">Dashboard</h1>
        <p className="text-sm text-muted-foreground">
          Visitas realizadas de {formatDate(data.from)} a {formatDate(data.to)}.
        </p>
      </div>
      <StatGrid label="Indicadores">
        <StatCard label="Meus Leads" value={data.myLeads} hint="agora" />
        <StatCard label="Visitas agendadas" value={data.scheduledVisits} hint="de hoje em diante" />
        <StatCard label="Visitas hoje" value={data.visitsToday} hint="agora" />
        <StatCard label="Convites ativos" value={data.activeInvitations} hint="agora" />
        <StatCard label="Visitas realizadas" value={data.completedVisits} hint="últimos 30 dias" />
      </StatGrid>

      <div className="grid gap-4 lg:grid-cols-2">
        <section aria-labelledby="upcoming-title" className="space-y-2 rounded-md border bg-background p-4">
          <div className="flex items-baseline justify-between gap-2">
            <h2 id="upcoming-title" className="font-semibold">
              Próximas visitas
            </h2>
            <Link className="text-sm underline" to="/agenda">
              Ver agenda
            </Link>
          </div>
          {data.upcomingVisits.length === 0 ? (
            <p className="text-sm text-muted-foreground">Nenhuma visita agendada a partir de hoje.</p>
          ) : (
            <ul className="divide-y">
              {data.upcomingVisits.map((visit) => (
                <li key={visit.visitId}>
                  <Link
                    to={`/visitas/${visit.visitId}`}
                    className="flex flex-wrap items-baseline justify-between gap-x-3 py-2 hover:bg-muted/50"
                  >
                    <span className="min-w-0 break-words font-medium">{visit.leadName}</span>
                    <span className="text-sm text-muted-foreground">
                      {formatDate(visit.scheduledDate)} ·{' '}
                      {visit.companionsCount === 1 ? '1 acompanhante' : `${visit.companionsCount} acompanhantes`}
                    </span>
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </section>
        {visits.data && <DayChart title="Minhas visitas por dia" days={visits.data.days} series={VISIT_SERIES} />}
      </div>
    </section>
  )
}
