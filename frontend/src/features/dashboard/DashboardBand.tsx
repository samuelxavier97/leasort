import type { ReactNode } from 'react'
import { formatDate } from '@/lib/format'
import type { Greeting } from './api'
import { greetingText, longDate, todaySentence } from './insights'

/**
 * Faixa na cor principal atrás do título, só no dashboard (D-128), de ponta a ponta da tela sem rolagem
 * horizontal (sombra recortada, em vez de 100vw). Todo texto sobre ela, inclusive o secundário, usa a cor
 * de texto da D-118, sem transparência. Os cartões do período sobem sobre a faixa.
 */
export function DashboardBand({
  today,
  greeting,
  userName,
  visitsToday,
  arrivedToday,
  from,
  to,
  children,
}: {
  today: string
  greeting: Greeting
  userName: string
  visitsToday: number
  arrivedToday: number
  from: string
  to: string
  children?: ReactNode
}) {
  return (
    <div
      data-testid="dashboard-band"
      className="-mt-6 bg-primary pt-6 pb-20 text-primary-foreground shadow-[0_0_0_100vmax_var(--primary)] [clip-path:inset(0_-100vmax)]"
    >
      <div className="flex flex-wrap items-end justify-between gap-x-6 gap-y-4">
        <div className="min-w-0 space-y-1">
          <div className="text-xs font-medium tracking-wider uppercase">
            <h1 className="inline">Dashboard</h1>
            <span aria-hidden="true"> · </span>
            <span>{longDate(today)}</span>
          </div>
          <p className="text-2xl font-semibold text-balance">{greetingText(greeting, userName)}</p>
          <p className="text-sm">{todaySentence(visitsToday, arrivedToday)}</p>
          <p className="text-sm">
            Período: {formatDate(from)} a {formatDate(to)}
          </p>
        </div>
        {children && <div className="flex flex-wrap items-end gap-3">{children}</div>}
      </div>
    </div>
  )
}
