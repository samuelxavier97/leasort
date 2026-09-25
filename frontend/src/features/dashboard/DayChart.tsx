import { Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { formatDate } from '@/lib/format'
import type { Series } from './series'


/** `2026-09-24` → `24/09`, para o eixo. */
function shortDate(isoDate: string): string {
  const [, month, day] = isoDate.split('-')
  return `${day}/${month}`
}

/**
 * Colunas empilhadas por dia (§16.7), com todos os dias do período. Um só eixo; legenda sempre visível;
 * tooltip ao passar o mouse; a tabela equivalente acompanha o gráfico (acessibilidade e cores de
 * contraste baixo, D-100).
 */
export function DayChart<K extends string>({
  title,
  days,
  series,
}: {
  title: string
  days: ({ date: string } & Record<K, number>)[]
  series: Series<K>[]
}) {
  const id = title.toLowerCase().replace(/\W+/g, '-')
  return (
    <section aria-labelledby={`${id}-title`} className="space-y-2 rounded-md border bg-background p-4">
      <h2 id={`${id}-title`} className="font-semibold">
        {title}
      </h2>
      <div className="h-64 w-full min-w-0" aria-hidden="true">
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={days} margin={{ top: 8, right: 8, bottom: 0, left: -16 }} barCategoryGap="20%">
            <CartesianGrid vertical={false} stroke="var(--border)" />
            <XAxis
              dataKey="date"
              tickFormatter={shortDate}
              tick={{ fontSize: 12, fill: 'var(--muted-foreground)' }}
              tickLine={false}
              axisLine={{ stroke: 'var(--border)' }}
              interval="preserveStartEnd"
              minTickGap={16}
            />
            <YAxis
              allowDecimals={false}
              tick={{ fontSize: 12, fill: 'var(--muted-foreground)' }}
              tickLine={false}
              axisLine={false}
            />
            <Tooltip
              cursor={{ fill: 'var(--muted)', opacity: 0.4 }}
              labelFormatter={(label) => formatDate(String(label))}
              contentStyle={{ borderRadius: 8, borderColor: 'var(--border)', fontSize: 13 }}
            />
            {/* Ordem fixa das séries e texto na cor de texto; a cor da série fica só no marcador. */}
            <Legend
              wrapperStyle={{ fontSize: 13 }}
              iconType="square"
              itemSorter={null}
              formatter={(value) => <span style={{ color: 'var(--foreground)' }}>{value}</span>}
            />
            {series.map((item, index) => (
              <Bar
                key={item.key}
                dataKey={item.key}
                name={item.label}
                stackId="day"
                fill={item.color}
                stroke="var(--chart-surface)"
                strokeWidth={2}
                maxBarSize={24}
                radius={index === series.length - 1 ? [4, 4, 0, 0] : 0}
                isAnimationActive={false}
              />
            ))}
          </BarChart>
        </ResponsiveContainer>
      </div>
      <details className="text-sm">
        <summary className="cursor-pointer text-muted-foreground">Ver tabela</summary>
        <div className="mt-2 max-h-72 overflow-auto">
          <table className="w-full text-left" aria-label={`${title} — tabela`}>
            <thead>
              <tr className="border-b">
                <th className="py-1 pr-3 font-medium">Data</th>
                {series.map((item) => (
                  <th key={item.key} className="py-1 pr-3 text-right font-medium">
                    {item.label}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {days.map((day) => (
                <tr key={day.date} className="border-b last:border-0">
                  <td className="py-1 pr-3">{formatDate(day.date)}</td>
                  {series.map((item) => (
                    <td key={item.key} className="py-1 pr-3 text-right tabular-nums">
                      {day[item.key]}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </details>
    </section>
  )
}
