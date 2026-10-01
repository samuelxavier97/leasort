import { DENIAL_REASON_LABELS } from '@/features/access/labels'
import type { Denials } from './api'
import { Panel } from './StatCard'

/**
 * "Por que a portaria negou" (D-129): as tentativas negadas do período por motivo, em barras horizontais na
 * cor fixa de negado (D-120), com o número sempre visível e a tabela equivalente (D-100). Cada tentativa
 * conta uma vez, inclusive as repetidas.
 */
export function DenialsChart({ data, filtered }: { data: Denials; filtered: boolean }) {
  const max = Math.max(1, ...data.reasons.map((reason) => reason.count))
  return (
    <Panel
      title="Por que a portaria negou"
      aside={data.total === 1 ? '1 tentativa negada' : `${data.total} tentativas negadas`}
    >
      {data.total === 0 ? (
        <p className="text-sm text-muted-foreground">Nenhuma negativa no período.</p>
      ) : (
        <ul className="space-y-2.5" aria-hidden="true">
          {data.reasons.map((reason) => (
            <li key={reason.reason} className="grid grid-cols-[minmax(0,9rem)_1fr_2.5rem] items-center gap-3 text-sm">
              <span className="truncate">{DENIAL_REASON_LABELS[reason.reason]}</span>
              <span className="h-2.5 rounded-full bg-muted">
                <span
                  className="block h-full rounded-full bg-status-denied"
                  style={{ width: `${(reason.count / max) * 100}%` }}
                />
              </span>
              <span className="text-right font-semibold tabular-nums">{reason.count}</span>
            </li>
          ))}
        </ul>
      )}
      {filtered && (
        <p className="text-xs text-muted-foreground">
          Com o filtro de Prospector, "Código inválido" não entra: a tentativa não tem convite.
        </p>
      )}
      <details className="text-sm">
        <summary className="cursor-pointer text-muted-foreground">Ver tabela</summary>
        <table className="mt-2 w-full text-left" aria-label="Por que a portaria negou — tabela">
          <thead>
            <tr className="border-b">
              <th className="py-1 pr-3 font-medium">Motivo</th>
              <th className="py-1 text-right font-medium">Tentativas</th>
            </tr>
          </thead>
          <tbody>
            {data.reasons.map((reason) => (
              <tr key={reason.reason} className="border-b last:border-0">
                <td className="py-1 pr-3">{DENIAL_REASON_LABELS[reason.reason]}</td>
                <td className="py-1 text-right tabular-nums">{reason.count}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </details>
    </Panel>
  )
}
