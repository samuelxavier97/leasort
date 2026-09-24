import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { FormAlert } from '@/components/FormAlert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { ApiError } from '@/lib/api'
import { errorMessage } from '@/lib/errors'
import { importLeads, LEAD_IMPORT_TEMPLATE_URL, leadsQueryKey, type ImportRowError } from './api'

const COLUMN_LABELS: Record<string, string> = {
  nome: 'Nome',
  cpf: 'CPF',
  telefone: 'Telefone',
  email: 'E-mail',
  data_nascimento: 'Data de nascimento',
  codigo_prospector: 'Código do Prospector',
  observacoes: 'Observações',
}

export function ImportLeadsPage() {
  const queryClient = useQueryClient()
  const [file, setFile] = useState<File | null>(null)

  const mutation = useMutation({
    mutationFn: (selected: File) => importLeads(selected),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: leadsQueryKey }),
  })

  const submit = (event: FormEvent) => {
    event.preventDefault()
    if (file) mutation.mutate(file)
  }

  const rejected = mutation.error instanceof ApiError && mutation.error.code === 'IMPORT_REJECTED'
  const rowErrors = rejected ? ((mutation.error as ApiError).body.errors as ImportRowError[]) : []

  return (
    <section className="max-w-3xl space-y-4">
      <Link className="text-sm text-muted-foreground hover:underline" to="/leads">
        ← Leads
      </Link>
      <h1 className="text-2xl font-semibold">Importar Leads</h1>

      <Card>
        <CardHeader>
          <CardTitle>Arquivo CSV</CardTitle>
        </CardHeader>
        <CardContent className="space-y-3 text-sm">
          <p>
            Use o modelo: separador <code>;</code>, UTF-8, datas em DD/MM/AAAA e até 5.000 linhas. Se alguma linha tiver
            erro, nenhum Lead é importado.
          </p>
          <a className="inline-block underline" href={LEAD_IMPORT_TEMPLATE_URL} download>
            Baixar modelo
          </a>
          <form className="flex flex-wrap items-end gap-2" onSubmit={submit}>
            <div className="space-y-1">
              <Label htmlFor="import-file">Arquivo</Label>
              <Input
                id="import-file"
                type="file"
                accept=".csv,text/csv"
                onChange={(event) => {
                  mutation.reset()
                  setFile(event.target.files?.[0] ?? null)
                }}
              />
            </div>
            <Button type="submit" disabled={!file || mutation.isPending}>
              {mutation.isPending ? 'Importando...' : 'Importar'}
            </Button>
          </form>
        </CardContent>
      </Card>

      {mutation.isSuccess && (
        <p role="status" className="rounded-md border border-green-600/30 bg-green-600/5 p-3 text-sm">
          {mutation.data.imported === 1 ? '1 Lead importado.' : `${mutation.data.imported} Leads importados.`}
        </p>
      )}
      {mutation.isError && <FormAlert message={errorMessage(mutation.error)} />}
      {rowErrors.length > 0 && (
        <div className="overflow-x-auto rounded-md border bg-background">
          <Table aria-label="Erros da importação">
            <TableHeader>
              <TableRow>
                <TableHead>Linha</TableHead>
                <TableHead>Coluna</TableHead>
                <TableHead>Problema</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {rowErrors.map((error, index) => (
                <TableRow key={`${error.line}-${error.column}-${index}`}>
                  <TableCell>{error.line}</TableCell>
                  <TableCell>{error.column ? (COLUMN_LABELS[error.column] ?? error.column) : '—'}</TableCell>
                  <TableCell>{error.message}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}
    </section>
  )
}
