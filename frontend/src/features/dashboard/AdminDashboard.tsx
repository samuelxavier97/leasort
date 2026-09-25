import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { FormAlert } from '@/components/FormAlert'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { listProspectors, prospectorsQueryKey } from '@/features/prospectors/api'
import { operationToday } from '@/lib/date'
import { errorMessage } from '@/lib/errors'
import { formatDate } from '@/lib/format'
import { dashboardQueryKey, getAccessByDay, getAdminSummary, getVisitsByDay, type DashboardParams } from './api'
import { DayChart } from './DayChart'
import { ACCESS_SERIES, VISIT_SERIES } from './series'
import { StatCard, StatGrid } from './StatCard'

/** `AAAA-MM-DD` menos `days` dias, sem fuso: é aritmética de datas de calendário. */
function minusDays(isoDate: string, days: number): string {
  const date = new Date(`${isoDate}T12:00:00Z`)
  date.setUTCDate(date.getUTCDate() - days)
  return date.toISOString().slice(0, 10)
}

/**
 * Dashboard do ADMIN (§16.7, D-098, D-099): filtros de período e Prospector numa linha acima dos números;
 * o mesmo filtro vale para o resumo e as duas séries. Período inválido não é enviado.
 */
export function AdminDashboard() {
  const today = operationToday()
  const [from, setFrom] = useState(() => minusDays(today, 29))
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

  const prospectors = useQuery({ queryKey: [...prospectorsQueryKey, 'all'], queryFn: () => listProspectors(0, 100) })
  const summary = useQuery({ queryKey: [...dashboardQueryKey, 'summary', applied], queryFn: () => getAdminSummary(applied) })
  const visits = useQuery({ queryKey: [...dashboardQueryKey, 'visits', applied], queryFn: () => getVisitsByDay(applied) })
  const access = useQuery({ queryKey: [...dashboardQueryKey, 'access', applied], queryFn: () => getAccessByDay(applied) })
  const error = summary.error ?? visits.error ?? access.error

  return (
    <section className="space-y-5">
      <h1 className="text-2xl font-semibold">Dashboard</h1>

      <div className="flex flex-wrap items-end gap-3" role="group" aria-label="Filtros">
        <div className="space-y-1">
          <Label htmlFor="dashboard-from">De</Label>
          <Input id="dashboard-from" type="date" className="w-40" value={from} onChange={(e) => update({ from: e.target.value })} />
        </div>
        <div className="space-y-1">
          <Label htmlFor="dashboard-to">Até</Label>
          <Input id="dashboard-to" type="date" className="w-40" value={to} onChange={(e) => update({ to: e.target.value })} />
        </div>
        <div className="space-y-1">
          <Label htmlFor="dashboard-prospector">Prospector</Label>
          <select
            id="dashboard-prospector"
            className="h-9 w-56 max-w-full rounded-md border border-input bg-transparent px-3 text-sm"
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
      </div>

      <FormAlert
        message={invalidPeriod ? 'A data inicial não pode ser posterior à final.' : error ? errorMessage(error) : null}
      />

      {summary.data && (
        <>
          <p className="text-sm text-muted-foreground">
            Período: {formatDate(summary.data.from)} a {formatDate(summary.data.to)} · Hoje: {formatDate(summary.data.today)}
          </p>
          <StatGrid label="Indicadores">
            <StatCard label="Total de Leads" value={summary.data.totalLeads} hint="agora" />
            <StatCard label="Leads atribuídos" value={summary.data.assignedLeads} hint="agora" />
            <StatCard label="Visitas agendadas" value={summary.data.scheduledVisits} hint="de hoje em diante" />
            <StatCard label="Visitas hoje" value={summary.data.visitsToday} hint="agora" />
            <StatCard label="Convites ativos" value={summary.data.activeInvitations} hint="agora" />
            <StatCard label="Visitas realizadas" value={summary.data.completedVisits} hint="no período" />
            <StatCard label="No-show" value={summary.data.noShows} hint="no período" />
            <StatCard label="Cancelamentos" value={summary.data.cancellations} hint="no período" />
            <StatCard label="Pessoas recebidas" value={summary.data.entries} hint="no período" />
          </StatGrid>
        </>
      )}
      {summary.isPending && <p className="text-muted-foreground">Carregando...</p>}

      <div className="grid gap-4 lg:grid-cols-2">
        {visits.data && <DayChart title="Visitas por dia" days={visits.data.days} series={VISIT_SERIES} />}
        {access.data && <DayChart title="Acessos por dia" days={access.data.days} series={ACCESS_SERIES} />}
      </div>
    </section>
  )
}
