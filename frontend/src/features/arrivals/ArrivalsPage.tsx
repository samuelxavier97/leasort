import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { useMe } from '@/features/auth/useMe'
import { ApiError } from '@/lib/api'
import { formatOperationTime, operationToday } from '@/lib/date'
import { errorMessage } from '@/lib/errors'
import { formatDate } from '@/lib/format'
import { arrivalsQueryKey, listArrivals } from './api'
import { ARRIVALS_POLLING_MS } from './labels'

/** Falha que não é da sessão (401 e troca de senha já têm destino): rede ou 5xx. */
function isTransient(error: unknown): boolean {
  return !(error instanceof ApiError) || error.status >= 500
}

/**
 * Chegadas (§16.6, D-095). HOST e PROSPECTOR: "Chegadas de hoje", sem data no pedido. ADMIN: "Chegadas",
 * com seletor de data. Polling de 30 s, só quando a lista é a de hoje.
 */
export function ArrivalsPage() {
  const { data: me } = useMe()
  const isAdmin = me?.role === 'ADMIN'
  const today = operationToday()
  const [date, setDate] = useState(today)
  const polling = !isAdmin || date === today

  const arrivals = useQuery({
    queryKey: [...arrivalsQueryKey, isAdmin ? date : 'today'],
    queryFn: () => listArrivals(isAdmin ? date : undefined),
    refetchInterval: polling ? ARRIVALS_POLLING_MS : false,
  })

  return (
    <section className="space-y-4">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold">{isAdmin ? 'Chegadas' : 'Chegadas de hoje'}</h1>
          {arrivals.data && (
            <p className="text-sm text-muted-foreground">
              Entradas de {formatDate(arrivals.data.date)}, da mais recente para a mais antiga.
            </p>
          )}
        </div>
        {isAdmin && (
          <div className="space-y-1">
            <Label htmlFor="arrivals-date">Data</Label>
            <Input
              id="arrivals-date"
              type="date"
              className="w-44"
              value={date}
              onChange={(event) => event.target.value && setDate(event.target.value)}
            />
          </div>
        )}
      </div>

      {arrivals.isRefetchError && isTransient(arrivals.error) && (
        <p role="status" className="rounded-md border border-amber-300 bg-amber-50 p-2 text-sm text-amber-900">
          Não foi possível atualizar. Nova tentativa em instantes.
        </p>
      )}

      {arrivals.isPending ? (
        <p className="text-muted-foreground">Carregando...</p>
      ) : !arrivals.data ? (
        <p className="text-destructive">{errorMessage(arrivals.error)}</p>
      ) : arrivals.data.arrivals.length === 0 ? (
        <p className="text-muted-foreground">Nenhuma chegada registrada neste dia.</p>
      ) : (
        <div className="rounded-md border bg-background">
          {/* Células quebram linha: no celular, o link da ficha fica sempre à vista, sem rolagem lateral. */}
          <Table className="[&_td]:whitespace-normal [&_td]:break-words [&_th]:whitespace-normal">
            <TableHeader>
              <TableRow>
                <TableHead>Hora</TableHead>
                <TableHead>Lead</TableHead>
                <TableHead>
                  {/* No celular o título longo empurraria a coluna da ficha para fora da tela. */}
                  <span aria-hidden="true" className="sm:hidden">
                    Presentes
                  </span>
                  <span className="sr-only sm:not-sr-only">Acompanhantes presentes</span>
                </TableHead>
                <TableHead>Prospector</TableHead>
                <TableHead>
                  <span className="sr-only">Ficha</span>
                </TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {arrivals.data.arrivals.map((arrival) => (
                <TableRow key={arrival.visitId}>
                  <TableCell>{formatOperationTime(arrival.entryAt)}</TableCell>
                  <TableCell className="font-medium">{arrival.leadName}</TableCell>
                  <TableCell>{arrival.companionsPresent}</TableCell>
                  <TableCell>{arrival.prospectorName}</TableCell>
                  <TableCell>
                    <Link
                      className="underline"
                      to={`/chegadas/${arrival.visitId}/ficha`}
                      aria-label={`Ficha de ${arrival.leadName}`}
                    >
                      Ficha
                    </Link>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}
    </section>
  )
}
