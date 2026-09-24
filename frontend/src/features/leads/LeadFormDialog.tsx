import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation } from '@tanstack/react-query'
import { useEffect } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { FieldError } from '@/components/FieldError'
import { FormAlert } from '@/components/FormAlert'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import type { Role } from '@/features/auth/types'
import type { Prospector } from '@/features/prospectors/api'
import { formatCpfInput, isValidCpf } from '@/lib/cpf'
import { errorMessage } from '@/lib/errors'
import { createLead, updateLead, type Lead, type LeadInput } from './api'

const today = () => new Date().toISOString().slice(0, 10)

const schema = z.object({
  name: z.string().trim().min(1, 'Informe o nome.').max(120, 'Nome muito longo.'),
  cpf: z.string().trim().refine((value) => value === '' || isValidCpf(value), 'CPF inválido.'),
  phone: z
    .string()
    .trim()
    .refine((value) => value === '' || /^[0-9+()\s-]{8,20}$/.test(value), 'Telefone inválido.'),
  email: z
    .string()
    .trim()
    .refine((value) => value === '' || z.email().safeParse(value).success, 'E-mail inválido.'),
  birthDate: z
    .string()
    .refine((value) => value === '' || (value >= '1900-01-01' && value <= today()), 'Data de nascimento inválida.'),
  notes: z.string().max(2000, 'Máximo de 2000 caracteres.'),
  prospectorId: z.string(),
})

type LeadForm = z.infer<typeof schema>

export function LeadFormDialog({
  open,
  lead,
  role,
  prospectors = [],
  onClose,
  onSaved,
}: {
  open: boolean
  lead: Lead | null
  role: Role
  prospectors?: Prospector[]
  onClose: () => void
  onSaved: (lead: Lead) => void
}) {
  const editing = lead !== null
  const isAdmin = role === 'ADMIN'
  // D-061: o PROSPECTOR só informa CPF quando o Lead não tem; o existente aparece mascarado e só leitura.
  const cpfLocked = editing && !isAdmin && lead.cpf !== null

  const {
    register,
    handleSubmit,
    reset,
    setValue,
    formState: { errors },
  } = useForm<LeadForm>({ resolver: zodResolver(schema) })

  useEffect(() => {
    if (open) {
      reset({
        name: lead?.name ?? '',
        cpf: cpfLocked ? '' : (lead?.cpf ?? ''),
        phone: lead?.phone ?? '',
        email: lead?.email ?? '',
        birthDate: lead?.birthDate ?? '',
        notes: lead?.notes ?? '',
        prospectorId: '',
      })
    }
  }, [open, lead, cpfLocked, reset])

  const mutation = useMutation({
    mutationFn: (form: LeadForm) => {
      const input: LeadInput = {
        name: form.name,
        cpf: cpfValue(form.cpf),
        phone: form.phone || null,
        email: form.email || null,
        birthDate: form.birthDate || null,
        notes: form.notes || null,
      }
      if (!editing) {
        input.prospectorId = form.prospectorId || null
        return createLead(input)
      }
      return updateLead(lead.id, input)
    },
    onSuccess: onSaved,
  })

  /** Sem CPF digitado: na criação, nenhum; na edição do ADMIN, remove (""); nos demais casos, mantém (null). */
  function cpfValue(typed: string): string | null {
    if (cpfLocked) return null
    if (typed) return typed
    return editing && isAdmin && lead.cpf !== null ? '' : null
  }

  const cpfField = register('cpf')

  return (
    <Dialog
      open={open}
      onOpenChange={(value) => {
        if (!value) {
          mutation.reset()
          onClose()
        }
      }}
    >
      <DialogContent className="max-h-[90svh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{editing ? 'Editar Lead' : 'Novo Lead'}</DialogTitle>
        </DialogHeader>
        <form className="space-y-4" noValidate onSubmit={handleSubmit((form) => mutation.mutate(form))}>
          <FormAlert message={mutation.isError ? errorMessage(mutation.error) : null} />
          <div className="space-y-2">
            <Label htmlFor="lead-name">Nome</Label>
            <Input id="lead-name" aria-invalid={!!errors.name} aria-describedby="lead-name-error" {...register('name')} />
            <FieldError id="lead-name-error" message={errors.name?.message} />
          </div>
          <div className="space-y-2">
            <Label htmlFor="lead-cpf">CPF</Label>
            {cpfLocked ? (
              <>
                <Input id="lead-cpf" value={lead.cpf ?? ''} readOnly disabled />
                <p className="text-sm text-muted-foreground">Só o administrador altera o CPF.</p>
              </>
            ) : (
              <>
                <Input
                  id="lead-cpf"
                  inputMode="numeric"
                  placeholder="000.000.000-00"
                  aria-invalid={!!errors.cpf}
                  aria-describedby="lead-cpf-error"
                  {...cpfField}
                  onChange={(event) => {
                    setValue('cpf', formatCpfInput(event.target.value))
                  }}
                />
                <FieldError id="lead-cpf-error" message={errors.cpf?.message} />
              </>
            )}
          </div>
          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-2">
              <Label htmlFor="lead-phone">Telefone</Label>
              <Input id="lead-phone" type="tel" aria-invalid={!!errors.phone} aria-describedby="lead-phone-error" {...register('phone')} />
              <FieldError id="lead-phone-error" message={errors.phone?.message} />
            </div>
            <div className="space-y-2">
              <Label htmlFor="lead-birth">Data de nascimento</Label>
              <Input id="lead-birth" type="date" aria-invalid={!!errors.birthDate} aria-describedby="lead-birth-error" {...register('birthDate')} />
              <FieldError id="lead-birth-error" message={errors.birthDate?.message} />
            </div>
          </div>
          <div className="space-y-2">
            <Label htmlFor="lead-email">E-mail</Label>
            <Input id="lead-email" type="email" aria-invalid={!!errors.email} aria-describedby="lead-email-error" {...register('email')} />
            <FieldError id="lead-email-error" message={errors.email?.message} />
          </div>
          <div className="space-y-2">
            <Label htmlFor="lead-notes">Notas internas</Label>
            <textarea
              id="lead-notes"
              rows={3}
              className="w-full rounded-md border border-input bg-transparent px-3 py-2 text-sm"
              aria-invalid={!!errors.notes}
              aria-describedby="lead-notes-error"
              {...register('notes')}
            />
            <FieldError id="lead-notes-error" message={errors.notes?.message} />
          </div>
          {!editing && isAdmin && (
            <div className="space-y-2">
              <Label htmlFor="lead-prospector">Prospector (opcional)</Label>
              <select
                id="lead-prospector"
                className="h-9 w-full rounded-md border border-input bg-transparent px-3 text-sm"
                {...register('prospectorId')}
              >
                <option value="">Não atribuído</option>
                {prospectors.filter((p) => p.active).map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.name} ({p.employeeCode})
                  </option>
                ))}
              </select>
            </div>
          )}
          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose}>
              Cancelar
            </Button>
            <Button type="submit" disabled={mutation.isPending}>
              {editing ? 'Salvar' : 'Criar Lead'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
