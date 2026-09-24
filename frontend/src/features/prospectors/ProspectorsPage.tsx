import { zodResolver } from '@hookform/resolvers/zod'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { useForm } from 'react-hook-form'
import { toast } from 'sonner'
import { z } from 'zod'
import { FieldError } from '@/components/FieldError'
import { FormAlert } from '@/components/FormAlert'
import { Pagination } from '@/components/Pagination'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { errorMessage } from '@/lib/errors'
import { listProspectors, prospectorsQueryKey, updateProspector, type Prospector } from './api'

const schema = z.object({
  employeeCode: z.string().trim().min(1, 'Informe o código de funcionário.').max(30, 'Código muito longo.'),
  phone: z
    .string()
    .trim()
    .refine((value) => value === '' || /^[0-9+()\s-]{8,20}$/.test(value), 'Telefone inválido.'),
})

type ProspectorForm = z.infer<typeof schema>

export function ProspectorsPage() {
  const queryClient = useQueryClient()
  const [page, setPage] = useState(0)
  const [editing, setEditing] = useState<Prospector | null>(null)

  const prospectors = useQuery({
    queryKey: [...prospectorsQueryKey, page],
    queryFn: () => listProspectors(page),
    placeholderData: keepPreviousData,
  })

  return (
    <section className="space-y-4">
      <h1 className="text-2xl font-semibold">Prospectores</h1>
      <p className="text-sm text-muted-foreground">
        Prospectores são criados em Usuários. Ativação e desativação também ficam em Usuários.
      </p>
      {prospectors.isError && <p className="text-destructive">{errorMessage(prospectors.error)}</p>}
      {prospectors.data && (
        <div className="overflow-x-auto rounded-md border bg-background">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Nome</TableHead>
                <TableHead>E-mail</TableHead>
                <TableHead>Código</TableHead>
                <TableHead>Telefone</TableHead>
                <TableHead>Situação</TableHead>
                <TableHead className="text-right">Ações</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {prospectors.data.content.map((prospector) => (
                <TableRow key={prospector.id}>
                  <TableCell>{prospector.name}</TableCell>
                  <TableCell>{prospector.email}</TableCell>
                  <TableCell>{prospector.employeeCode}</TableCell>
                  <TableCell>{prospector.phone ?? '—'}</TableCell>
                  <TableCell>
                    <Badge variant={prospector.active ? 'secondary' : 'outline'}>
                      {prospector.active ? 'Ativo' : 'Inativo'}
                    </Badge>
                  </TableCell>
                  <TableCell className="text-right">
                    <Button variant="ghost" size="sm" onClick={() => setEditing(prospector)}>
                      Editar
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}
      {prospectors.data && (
        <Pagination page={prospectors.data.page} totalPages={prospectors.data.totalPages} onChange={setPage} />
      )}
      <ProspectorFormDialog
        prospector={editing}
        onClose={() => setEditing(null)}
        onSaved={() => {
          setEditing(null)
          toast.success('Prospector atualizado.')
          queryClient.invalidateQueries({ queryKey: prospectorsQueryKey })
        }}
      />
    </section>
  )
}

function ProspectorFormDialog({
  prospector,
  onClose,
  onSaved,
}: {
  prospector: Prospector | null
  onClose: () => void
  onSaved: () => void
}) {
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<ProspectorForm>({ resolver: zodResolver(schema) })

  useEffect(() => {
    if (prospector) {
      reset({ employeeCode: prospector.employeeCode, phone: prospector.phone ?? '' })
    }
  }, [prospector, reset])

  const mutation = useMutation({
    mutationFn: (form: ProspectorForm) =>
      updateProspector(prospector!.id, { employeeCode: form.employeeCode, phone: form.phone || null }),
    onSuccess: onSaved,
  })

  return (
    <Dialog
      open={prospector !== null}
      onOpenChange={(open) => {
        if (!open) {
          mutation.reset()
          onClose()
        }
      }}
    >
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Editar Prospector</DialogTitle>
        </DialogHeader>
        <form className="space-y-4" noValidate onSubmit={handleSubmit((form) => mutation.mutate(form))}>
          <FormAlert message={mutation.isError ? errorMessage(mutation.error) : null} />
          <p className="text-sm">{prospector?.name}</p>
          <div className="space-y-2">
            <Label htmlFor="prospector-code">Código de funcionário</Label>
            <Input
              id="prospector-code"
              aria-invalid={!!errors.employeeCode}
              aria-describedby="prospector-code-error"
              {...register('employeeCode')}
            />
            <FieldError id="prospector-code-error" message={errors.employeeCode?.message} />
          </div>
          <div className="space-y-2">
            <Label htmlFor="prospector-phone">Telefone</Label>
            <Input
              id="prospector-phone"
              type="tel"
              aria-invalid={!!errors.phone}
              aria-describedby="prospector-phone-error"
              {...register('phone')}
            />
            <FieldError id="prospector-phone-error" message={errors.phone?.message} />
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose}>
              Cancelar
            </Button>
            <Button type="submit" disabled={mutation.isPending}>
              Salvar
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
