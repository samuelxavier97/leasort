import { cn } from '@/lib/utils'
import type { ProspectorPerformance } from './api'
import { formatPercent } from './insights'
import { Panel } from './StatCard'

/**
 * "Desempenho por Prospector" (D-128), só do ADMIN: realizadas, sem comparecimento e taxa de
 * comparecimento no período, na ordem do backend. O 1º lugar leva o detalhe (`--brand-accent`): dourado na
 * identidade do Resortric, a cor principal com tema de cliente. Clicar numa linha aplica o filtro daquele
 * Prospector no dashboard; clicar de novo tira.
 */
export function PerformanceTable({
  data,
  selected,
  onSelect,
}: {
  data: ProspectorPerformance
  selected: string
  onSelect: (prospectorId: string) => void
}) {
  return (
    <Panel title="Desempenho por Prospector" aside="ordenado por visitas realizadas">
      {data.prospectors.length === 0 ? (
        <p className="text-sm text-muted-foreground">Nenhuma visita realizada ou sem comparecimento no período.</p>
      ) : (
        <table className="table-cards w-full text-left text-sm">
          <thead className="bg-muted">
            <tr>
              <th className="px-2 py-2 font-medium">
                <span className="sr-only">Posição</span>
              </th>
              <th className="px-2 py-2 font-medium">Prospector</th>
              <th className="px-2 py-2 text-right font-medium">Realizadas</th>
              <th className="px-2 py-2 text-right font-medium">Sem comparecimento</th>
              <th className="px-2 py-2 font-medium">Comparecimento</th>
            </tr>
          </thead>
          <tbody>
            {data.prospectors.map((row, index) => {
              const isSelected = row.prospectorId === selected
              return (
                <tr
                  key={row.prospectorId}
                  onClick={() => onSelect(row.prospectorId)}
                  className={cn('cursor-pointer border-b last:border-0 hover:bg-muted/60', isSelected && 'bg-soft-strong')}
                >
                  <td className="px-2 py-2" data-label="Posição">
                    <span
                      data-testid={index === 0 ? 'first-place' : undefined}
                      className={cn(
                        'grid size-6 place-items-center rounded-full text-xs font-semibold',
                        index === 0 ? 'bg-brand-accent text-brand-accent-foreground' : 'bg-soft-strong',
                      )}
                    >
                      {index + 1}
                    </span>
                  </td>
                  <td className="px-2 py-2" data-label="Prospector">
                    <button
                      type="button"
                      aria-pressed={isSelected}
                      aria-label={`${isSelected ? 'Tirar o filtro de' : 'Filtrar por'} ${row.name}`}
                      onClick={(event) => {
                        event.stopPropagation()
                        onSelect(row.prospectorId)
                      }}
                      className={cn('text-left hover:underline', isSelected && 'font-semibold')}
                    >
                      {row.name}
                    </button>
                  </td>
                  <td className="px-2 py-2 text-right tabular-nums" data-label="Realizadas">
                    {row.completedVisits}
                  </td>
                  <td className="px-2 py-2 text-right tabular-nums" data-label="Sem comparecimento">
                    {row.noShows}
                  </td>
                  <td className="px-2 py-2" data-label="Comparecimento">
                    <span className="flex items-center justify-end gap-2 sm:justify-start">
                      <span className="hidden h-2 w-24 rounded-full bg-muted sm:block" aria-hidden="true">
                        <span className="block h-full rounded-full bg-primary-edge" style={{ width: `${row.attendanceRate * 100}%` }} />
                      </span>
                      <span className="tabular-nums">{formatPercent(row.attendanceRate)}</span>
                    </span>
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      )}
    </Panel>
  )
}
