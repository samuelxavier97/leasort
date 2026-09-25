import { Card, CardContent } from '@/components/ui/card'

/** Número de destaque (§16.7): rótulo e valor em tinta de texto, nunca na cor de série. */
export function StatCard({ label, value, hint }: { label: string; value: number; hint?: string }) {
  return (
    <Card className="gap-0 py-4">
      <CardContent className="space-y-1 px-4">
        <p className="text-sm text-muted-foreground">{label}</p>
        <p className="text-3xl font-semibold tabular-nums" data-testid="stat-value">
          {value.toLocaleString('pt-BR')}
        </p>
        {hint && <p className="text-xs text-muted-foreground">{hint}</p>}
      </CardContent>
    </Card>
  )
}

export function StatGrid({ children, label }: { children: React.ReactNode; label: string }) {
  return (
    <section aria-label={label} className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
      {children}
    </section>
  )
}
