import { useQuery } from '@tanstack/react-query'
import { CalendarCheck, CalendarX, Percent, UserX, Users } from 'lucide-react'
import { useState } from 'react'
import { FormAlert } from '@/components/FormAlert'
import { PageLoading } from '@/components/PageState'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { useMe } from '@/features/auth/useMe'
import { listProspectors, prospectorsQueryKey } from '@/features/prospectors/api'
import { operationToday } from '@/lib/date'
import { errorMessage } from '@/lib/errors'
import {
  dashboardQueryKey,
  getAdminSummary,
  getDenials,
  getProspectorPerformance,
  getVisitsByDay,
  type DashboardParams,
} from './api'
import { DashboardBand } from './DashboardBand'
import { DayChart } from './DayChart'
import { DenialsChart } from './DenialsChart'
import { attendanceRate, compareCount, compareRate, formatPercent, periodDays, presetPeriod } from './insights'
import { PerformanceTable } from './PerformanceTable'
import { PeriodSelector, type PeriodChoice } from './PeriodSelector'
import { VISIT_SERIES } from './series'
import { MetricCard, StatCard, StatGrid } from './StatCard'
import { TodayPanel } from './TodayPanel'

const FIELD = 'h-9 rounded-md border border-input bg-background px-3 text-sm text-foreground'

/**
 * Dashboard do ADMIN (§16.7, D-098, D-128, D-129). Na faixa: a saudação, o resumo de hoje e os filtros
 * (período em botões, com "Personalizado" para as duas datas, e Prospector), aplicados juntos a todos os
 * números; o desempenho por Prospector compara todos e, ao clicar numa linha, aplica o filtro dele.
 */
export function AdminDashboard() {
  const { data: me } = useMe()
  const today = operationToday()
  const [choice, setChoice] = useState<PeriodChoice>(30)
  const [from, setFrom] = useState(() => presetPeriod(30, today).from)
  const [to, setTo] = useState(today)
  const [prospectorId, setProspectorId] = useState('')
  const invalidPeriod = !from || !to || from > to
  const [applied, setApplied] = useState<DashboardParams>({ from, to })

  function update(next: { from?: string; to?: string; prospectorId?: string }) {
    const nextFrom = next.from ?? from
    const nextTo = next.to ?? to
    const nextProspector = next.prospectorId ?? prospectorId
    setFrom(nextFrom)
    setTo(nextTo)
    setProspectorId(nextProspector)
    if (nextFrom && nextTo && nextFrom <= nextTo) {
      setApplied({ from: nextFrom, to: nextTo, prospectorId: nextProspector || undefined })
    }
  }

  function choose(next: PeriodChoice) {
    setChoice(next)
    if (next !== 'custom') {
      update(presetPeriod(next, today))
    }
  }

  const prospectors = useQuery({ queryKey: [...prospectorsQueryKey, 'all'], queryFn: () => listProspectors(0, 100) })
  const summary = useQuery({ queryKey: [...dashboardQueryKey, 'summary', applied], queryFn: () => getAdminSummary(applied) })
  const visits = useQuery({ queryKey: [...dashboardQueryKey, 'visits', applied], queryFn: () => getVisitsByDay(applied) })
  const denials = useQuery({ queryKey: [...dashboardQueryKey, 'denials', applied], queryFn: () => getDenials(applied) })
  const performance = useQuery({
    queryKey: [...dashboardQueryKey, 'performance', applied.from, applied.to],
    queryFn: () => getProspectorPerformance(applied),
  })
  const error = summary.error ?? visits.error ?? denials.error ?? performance.error

  const data = summary.data
  const days = data ? periodDays(data.from, data.to) : 30
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
        from={data?.from ?? from}
        to={data?.to ?? to}
      >
        <PeriodSelector value={choice} onChange={choose} allowCustom />
        {choice === 'custom' && (
          <>
            <div className="space-y-1">
              <Label htmlFor="dashboard-from">De</Label>
              <Input id="dashboard-from" type="date" className="w-40" value={from} onChange={(e) => update({ from: e.target.value })} />
            </div>
            <div className="space-y-1">
              <Label htmlFor="dashboard-to">Até</Label>
              <Input id="dashboard-to" type="date" className="w-40" value={to} onChange={(e) => update({ to: e.target.value })} />
            </div>
          </>
        )}
        <div className="space-y-1">
          <Label htmlFor="dashboard-prospector">Prospector</Label>
          <select
            id="dashboard-prospector"
            className={`${FIELD} w-56 max-w-full`}
            value={prospectorId}
            onChange={(e) => update({ prospectorId: e.target.value })}
          >
            <option value="">Todos</option>
            {prospectors.data?.content.map((prospector) => (
              <option key={prospector.id} value={prospector.id}>
                {prospector.name} ({prospector.employeeCode})
              </option>
            ))}
          </select>
        </div>
      </DashboardBand>

      <div className="relative -mt-20 space-y-5">
        <FormAlert
          message={invalidPeriod ? 'A data inicial não pode ser posterior à final.' : error ? errorMessage(error) : null}
        />
        {data ? (
          <>
            <StatGrid label="No período">
              <MetricCard
                label="Visitas realizadas"
                value={data.completedVisits.toLocaleString('pt-BR')}
                icon={CalendarCheck}
                comparison={compareCount(data.completedVisits, data.previous.completedVisits, days, 'higher')}
              />
              <MetricCard
                label="Comparecimento"
                value={formatPercent(rate)}
                icon={Percent}
                comparison={compareRate(rate, previousRate, days, 'higher')}
              />
              <MetricCard
                label="Sem comparecimento"
                value={data.noShows.toLocaleString('pt-BR')}
                icon={UserX}
                comparison={compareCount(data.noShows, data.previous.noShows, days, 'lower')}
              />
              <MetricCard
                label="Cancelamentos"
                value={data.cancellations.toLocaleString('pt-BR')}
                icon={CalendarX}
                comparison={compareCount(data.cancellations, data.previous.cancellations, days, 'lower')}
              />
              <MetricCard
                label="Pessoas recebidas"
                value={data.entries.toLocaleString('pt-BR')}
                icon={Users}
                comparison={compareCount(data.entries, data.previous.entries, days, 'higher')}
              />
            </StatGrid>
            <div className="space-y-2">
              <h2 className="text-sm font-medium text-muted-foreground">Agora</h2>
              <StatGrid label="Agora">
                <StatCard label="Total de Leads" value={data.totalLeads} hint="agora" />
                <StatCard label="Leads atribuídos" value={data.assignedLeads} hint="agora" />
                <StatCard label="Visitas agendadas" value={data.scheduledVisits} hint="de hoje em diante" />
                <StatCard label="Visitas hoje" value={data.visitsToday} hint="agora" />
                <StatCard label="Convites ativos" value={data.activeInvitations} hint="agora" />
              </StatGrid>
            </div>
          </>
        ) : (
          summary.isPending && <PageLoading />
        )}

        <div className="grid gap-4 lg:grid-cols-5">
          {visits.data && (
            <div className="min-w-0 lg:col-span-3">
              <DayChart title="Visitas por dia" days={visits.data.days} series={VISIT_SERIES} />
            </div>
          )}
          {data && (
            <div className="min-w-0 lg:col-span-2">
              <TodayPanel visits={data.todayVisits} total={data.visitsToday} />
            </div>
          )}
        </div>
        <div className="grid gap-4 lg:grid-cols-5">
          {performance.data && (
            <div className="min-w-0 lg:col-span-3">
              <PerformanceTable
                data={performance.data}
                selected={prospectorId}
                onSelect={(id) => update({ prospectorId: id === prospectorId ? '' : id })}
              />
            </div>
          )}
          {denials.data && (
            <div className="min-w-0 lg:col-span-2">
              <DenialsChart data={denials.data} filtered={Boolean(applied.prospectorId)} />
            </div>
          )}
        </div>
      </div>
    </section>
  )
}
