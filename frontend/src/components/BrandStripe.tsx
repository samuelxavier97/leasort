import { cn } from '@/lib/utils'

/**
 * Faixa de 4 px no topo (D-117) na cor principal. O último trecho é o dourado do Resortric; com tema de
 * cliente, `--brand-accent` é a própria cor principal, e o trecho some (D-126).
 */
export function BrandStripe({ className }: { className?: string }) {
  return (
    <div aria-hidden="true" data-testid="brand-stripe" className={cn('relative h-1 bg-primary', className)}>
      <div className="absolute inset-y-0 right-0 w-[15%] bg-brand-accent" />
    </div>
  )
}
