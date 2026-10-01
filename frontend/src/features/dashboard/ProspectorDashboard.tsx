import { useQuery } from '@tanstack/react-query'
import { CalendarCheck, Percent } from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router'
import { LoadError, PageLoading } from '@/components/PageState'
import { useMe } from '@/features/auth/useMe'
import { operationToday } from '@/lib/date'
import { formatDate } from '@/lib/format'
import { dashboardQueryKey, getProspectorSummary, getVisitsByDay } from './api'
import { DashboardBand } from './DashboardBand'
import { DayChart } from './DayChart'
import { attendanceRate, compareCount, compareRate, formatPercent, periodDays, presetPeriod, type Preset } from './insights'
import { PeriodSelector } from './PeriodSelector'
import { VISIT_SERIES } from './series'
import { MetricCard, Panel, StatCard, StatGrid } from './StatCard'
import { TodayPanel } from './TodayPanel'

/**
 * Dashboard do PROSPECTOR (§16.7, D-128): sempre os próprios números. Escolhe só o período, em 7, 30 ou 90
 * dias até hoje (mudança da D-100); sem filtro de Prospector, que o backend recusa (D-098).
 */
export function ProspectorDashboard() {
  const { data: me } = useMe()
  const today = operationToday()
  const [days, setDays] = useState<Preset>(30)
  const period = presetPeriod(days, today)
  const summary = useQuery({ queryKey: [...dashboardQueryKey, 'summary', 'mine', period], queryFn: () => getProspectorSummary(period) })
  const visits = useQuery({ queryKey: [...dashboardQueryKey, 'visits', 'mine', period], queryFn: () => getVisitsByDay(period) })

  const data = summary.data
  const length = data ? periodDays(data.from, data.to) : days
  const rate = data ? attendanceRate(data.completedVisits, data.noShows) : null
  const previousRate = data ? attendanceRate(data.previous.completedVisits, data.previous.noShows) : null

  return (
    <section className="space-y-5">
      <DashboardBand
        today={data?.today ?? today}
        greeting={data?.greeting ?? 'MORNING'}
        userName={me?.name ?? ''}
        visitsToday={data?.visitsToday ?? 0}
        arrivedToday={data?.arrivedToday ?? 0}
        from={data?.from ?? period.from}
        to={data?.to ?? period.to}
      >
        <PeriodSelector value={days} onChange={(choice) => choice !== 'custom' && setDays(choice)} allowCustom={false} />
      </DashboardBand>

      <div className="relative -mt-20 space-y-5">
        {!data ? (
          summary.isError ? <LoadError error={summary.error} onRetry={() => summary.refetch()} /> : <PageLoading />
        ) : (
          <>
            <StatGrid label="Indicadores" className="lg:grid-cols-6">
              <MetricCard
                label="Visitas realizadas"
                value={data.completedVisits.toLocaleString('pt-BR')}
                icon={CalendarCheck}
                comparison={compareCount(data.completedVisits, data.previous.completedVisits, length, 'higher')}
              />
              <MetricCard
                label="Comparecimento"
                value={formatPercent(rate)}
                icon={Percent}
                comparison={compareRate(rate, previousRate, length, 'higher')}
              />
              <StatCard label="Meus Leads" value={data.myLeads} hint="agora" />
              <StatCard label="Visitas agendadas" value={data.scheduledVisits} hint="de hoje em diante" />
              <StatCard label="Visitas hoje" value={data.visitsToday} hint="agora" />
              <StatCard label="Convites ativos" value={data.activeInvitations} hint="agora" />
            </StatGrid>

            <div className="grid gap-4 lg:grid-cols-2">
              <TodayPanel visits={data.todayVisits} total={data.visitsToday} />
              <Panel
                title="Próximas visitas"
                aside={
                  <Link className="text-foreground underline" to="/agenda">
                    Ver agenda
                  </Link>
                }
              >
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
                          <span className="min-w-0 font-medium break-words">{visit.leadName}</span>
                          <span className="text-sm text-muted-foreground">
                            {formatDate(visit.scheduledDate)} ·{' '}
                            {visit.companionsCount === 1 ? '1 acompanhante' : `${visit.companionsCount} acompanhantes`}
                          </span>
                        </Link>
                      </li>
                    ))}
                  </ul>
                )}
              </Panel>
            </div>
            {visits.data && <DayChart title="Minhas visitas por dia" days={visits.data.days} series={VISIT_SERIES} />}
          </>
        )}
      </div>
    </section>
  )
}
