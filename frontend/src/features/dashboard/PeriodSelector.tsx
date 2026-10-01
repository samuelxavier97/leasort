import { cn } from '@/lib/utils'
import { PRESETS, type Preset } from './insights'

export type PeriodChoice = Preset | 'custom'

/**
 * Período em botões (D-128): 7, 30 e 90 dias até hoje. O ADMIN tem também "Personalizado", que mantém os
 * dois campos de data de antes (D-100); o PROSPECTOR, só os três botões (mudança da D-100).
 */
export function PeriodSelector({
  value,
  onChange,
  allowCustom,
}: {
  value: PeriodChoice
  onChange: (choice: PeriodChoice) => void
  allowCustom: boolean
}) {
  const choices: PeriodChoice[] = allowCustom ? [...PRESETS, 'custom'] : [...PRESETS]
  return (
    <div role="group" aria-label="Período" className="inline-flex flex-wrap gap-1 rounded-lg border bg-card p-1 text-card-foreground shadow-sm">
      {choices.map((choice) => (
        <button
          key={choice}
          type="button"
          aria-pressed={value === choice}
          onClick={() => onChange(choice)}
          className={cn(
            'rounded-md px-3 py-1.5 text-sm font-medium',
            value === choice ? 'bg-primary text-primary-foreground' : 'hover:bg-muted',
          )}
        >
          {choice === 'custom' ? 'Personalizado' : `${choice} dias`}
        </button>
      ))}
    </div>
  )
}
