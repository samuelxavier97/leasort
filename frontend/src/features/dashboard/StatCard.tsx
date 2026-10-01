import type { LucideIcon } from 'lucide-react'
import { cn } from '@/lib/utils'
import type { Comparison } from './insights'

const TONES: Record<Comparison['tone'], string> = {
  good: 'text-status-ok',
  bad: 'text-status-denied',
  neutral: 'text-muted-foreground',
}

/**
 * Indicador de período (D-128): rótulo, chip de ícone, valor e a comparação com o período anterior. A cor
 * diz se a mudança é boa ou ruim e nunca vem sozinha: há sempre a seta e o texto, e "melhora" ou "piora"
 * para o leitor de tela. Valor em tinta de texto, nunca na cor de série (§16.7).
 */
export function MetricCard({
  label,
  value,
  icon: Icon,
  comparison,
}: {
  label: string
  value: string
  icon: LucideIcon
  comparison: Comparison
}) {
  const arrow = /^[▲▼=]/.test(comparison.text) ? comparison.text[0] : null
  const text = arrow ? comparison.text.slice(1).trim() : comparison.text
  return (
    <div className="space-y-2 rounded-xl border bg-card p-4 text-card-foreground shadow-sm">
      <div className="flex items-start justify-between gap-2">
        <p className="text-sm font-medium">{label}</p>
        <span className="grid size-9 shrink-0 place-items-center rounded-lg bg-soft-strong text-primary-edge" aria-hidden="true">
          <Icon className="size-5" />
        </span>
      </div>
      <p className="text-3xl font-semibold tabular-nums" data-testid="stat-value">
        {value}
      </p>
      <p className={cn('text-xs', TONES[comparison.tone])} data-testid="stat-comparison">
        {arrow && <span aria-hidden="true">{arrow} </span>}
        {text}
        {comparison.tone !== 'neutral' && <span className="sr-only"> ({comparison.tone === 'good' ? 'melhora' : 'piora'})</span>}
      </p>
    </div>
  )
}

/** Fotografia do momento (D-098): número compacto com a dica ("agora", "de hoje em diante"). */
export function StatCard({ label, value, hint }: { label: string; value: number; hint?: string }) {
  return (
    <div className="space-y-1 rounded-xl border bg-card px-4 py-3 text-card-foreground">
      <p className="text-sm text-muted-foreground">{label}</p>
      <p className="text-2xl font-semibold tabular-nums" data-testid="stat-value">
        {value.toLocaleString('pt-BR')}
      </p>
      {hint && <p className="text-xs text-muted-foreground">{hint}</p>}
    </div>
  )
}

export function StatGrid({ children, label, className }: { children: React.ReactNode; label: string; className?: string }) {
  return (
    <section aria-label={label} className={cn('grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5', className)}>
      {children}
    </section>
  )
}

/** Moldura das seções do dashboard: cartão branco com título e, à direita, uma nota ou um link. */
export function Panel({
  title,
  aside,
  children,
  className,
}: {
  title: string
  aside?: React.ReactNode
  children: React.ReactNode
  className?: string
}) {
  const id = `${title.toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/\W+/g, '-')}-title`
  return (
    <section aria-labelledby={id} className={cn('min-w-0 space-y-3 rounded-xl border bg-card p-4 shadow-sm sm:p-5', className)}>
      <div className="flex flex-wrap items-baseline justify-between gap-x-3 gap-y-1">
        <h2 id={id} className="font-semibold">
          {title}
        </h2>
        {aside && <div className="text-sm text-muted-foreground">{aside}</div>}
      </div>
      {children}
    </section>
  )
}
