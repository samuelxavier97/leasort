import { CircleAlert, CircleCheck, Loader2, TriangleAlert } from 'lucide-react'
import type { ReactNode } from 'react'
import { Button } from '@/components/ui/button'
import { errorMessage } from '@/lib/errors'
import { cn } from '@/lib/utils'

/** Estados comuns das telas (D-122): carregando, vazio, erro ao carregar e aviso. */

export function PageLoading({ label = 'Carregando...' }: { label?: string }) {
  return (
    <p role="status" className="flex items-center gap-2 text-muted-foreground">
      <Loader2 aria-hidden="true" className="size-4 animate-spin" />
      {label}
    </p>
  )
}

export function EmptyState({ children }: { children: ReactNode }) {
  return <p className="rounded-md border border-dashed bg-background p-6 text-center text-muted-foreground">{children}</p>
}

/** Erro ao carregar: a mensagem em português e, quando há como, "Tentar de novo". */
export function LoadError({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  return (
    <div role="alert" className="flex flex-wrap items-center gap-3 rounded-md border border-destructive/30 bg-destructive/5 p-3 text-sm text-destructive">
      <CircleAlert aria-hidden="true" className="size-4 shrink-0" />
      <span className="min-w-0 flex-1">{errorMessage(error)}</span>
      {onRetry && (
        <Button variant="outline" size="sm" onClick={onRetry}>
          Tentar de novo
        </Button>
      )}
    </div>
  )
}

/** Aviso em cores fixas, fora do tema do cliente (D-120): âmbar para atenção, verde para sucesso. */
export function Notice({ tone, children, className }: { tone: 'warning' | 'success'; children: ReactNode; className?: string }) {
  const Icon = tone === 'warning' ? TriangleAlert : CircleCheck
  return (
    <p
      role="status"
      className={cn(
        'flex items-start gap-2 rounded-md border p-3 text-sm',
        tone === 'warning' ? 'border-amber-300 bg-amber-50 text-amber-900' : 'border-green-700/30 bg-green-50 text-green-900',
        className,
      )}
    >
      <Icon aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
      <span>{children}</span>
    </p>
  )
}
