import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation } from '@tanstack/react-query'
import { useEffect } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { z } from 'zod'
import { ROLE_LABELS } from '@/app/navigation'
import { FieldError } from '@/components/FieldError'
import { FormAlert } from '@/components/FormAlert'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import type { Role } from '@/features/auth/types'
import { errorMessage } from '@/lib/errors'
import { createUser, updateUser, type User } from './api'

const schema = z
  .object({
    name: z.string().trim().min(1, 'Informe o nome.').max(120, 'Nome muito longo.'),
    email: z.string().trim().min(1, 'Informe o e-mail.').email('E-mail inválido.'),
    role: z.enum(['ADMIN', 'PROSPECTOR', 'GATE', 'HOST']),
    employeeCode: z.string().trim().max(30, 'Código muito longo.'),
    phone: z
      .string()
      .trim()
      .refine((value) => value === '' || /^[0-9+()\s-]{8,20}$/.test(value), 'Telefone inválido.'),
  })
  .refine((form) => form.role !== 'PROSPECTOR' || form.employeeCode !== '', {
    path: ['employeeCode'],
    message: 'Informe o código de funcionário.',
  })

type UserForm = z.infer<typeof schema>

/** Perfis que um usuário existente pode assumir (D-042). */
const SWITCHABLE_ROLES: Role[] = ['ADMIN', 'GATE', 'HOST']

export function UserFormDialog({
  open,
  user,
  onClose,
  onCreated,
  onUpdated,
}: {
  open: boolean
  user: User | null
  onClose: () => void
  onCreated: (user: User, temporaryPassword: string) => void
  onUpdated: () => void
}) {
  const editing = user !== null
  const {
    register,
    handleSubmit,
    control,
    reset,
    formState: { errors },
  } = useForm<UserForm>({ resolver: zodResolver(schema) })

  useEffect(() => {
    if (open) {
      reset({
        name: user?.name ?? '',
        email: user?.email ?? '',
        role: user?.role ?? 'PROSPECTOR',
        employeeCode: '',
        phone: '',
      })
    }
  }, [open, user, reset])

  const mutation = useMutation({
    mutationFn: async (form: UserForm) => {
      if (user) {
        await updateUser(user.id, { name: form.name, email: form.email, role: form.role })
        return null
      }
      const prospector = form.role === 'PROSPECTOR'
      return createUser({
        name: form.name,
        email: form.email,
        role: form.role,
        employeeCode: prospector ? form.employeeCode : undefined,
        phone: prospector && form.phone ? form.phone : undefined,
      })
    },
    onSuccess: (created) => {
      if (created) {
        onCreated(created.user, created.temporaryPassword)
      } else {
        onUpdated()
      }
    },
  })

  const role = useWatch({ control, name: 'role' })
  const roleOptions: Role[] = !editing
    ? ['ADMIN', 'PROSPECTOR', 'GATE', 'HOST']
    : user.role === 'PROSPECTOR'
      ? ['PROSPECTOR']
      : SWITCHABLE_ROLES

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
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{editing ? 'Editar usuário' : 'Novo usuário'}</DialogTitle>
        </DialogHeader>
        <form className="space-y-4" noValidate onSubmit={handleSubmit((form) => mutation.mutate(form))}>
          <FormAlert message={mutation.isError ? errorMessage(mutation.error) : null} />
          <div className="space-y-2">
            <Label htmlFor="user-name">Nome</Label>
            <Input id="user-name" aria-invalid={!!errors.name} aria-describedby="user-name-error" {...register('name')} />
            <FieldError id="user-name-error" message={errors.name?.message} />
          </div>
          <div className="space-y-2">
            <Label htmlFor="user-email">E-mail</Label>
            <Input
              id="user-email"
              type="email"
              aria-invalid={!!errors.email}
              aria-describedby="user-email-error"
              {...register('email')}
            />
            <FieldError id="user-email-error" message={errors.email?.message} />
          </div>
          <div className="space-y-2">
            <Label htmlFor="user-role">Perfil</Label>
            <select
              id="user-role"
              className="h-9 w-full rounded-md border border-input bg-transparent px-3 text-sm disabled:opacity-50"
              disabled={roleOptions.length === 1}
              {...register('role')}
            >
              {roleOptions.map((option) => (
                <option key={option} value={option}>
                  {ROLE_LABELS[option]}
                </option>
              ))}
            </select>
            {editing && user.role === 'PROSPECTOR' && (
              <p className="text-sm text-muted-foreground">O perfil de Prospector não pode ser trocado.</p>
            )}
          </div>
          {!editing && role === 'PROSPECTOR' && (
            <>
              <div className="space-y-2">
                <Label htmlFor="user-employee-code">Código de funcionário</Label>
                <Input
                  id="user-employee-code"
                  aria-invalid={!!errors.employeeCode}
                  aria-describedby="user-employee-code-error"
                  {...register('employeeCode')}
                />
                <FieldError id="user-employee-code-error" message={errors.employeeCode?.message} />
              </div>
              <div className="space-y-2">
                <Label htmlFor="user-phone">Telefone (opcional)</Label>
                <Input
                  id="user-phone"
                  type="tel"
                  aria-invalid={!!errors.phone}
                  aria-describedby="user-phone-error"
                  {...register('phone')}
                />
                <FieldError id="user-phone-error" message={errors.phone?.message} />
              </div>
            </>
          )}
          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose}>
              Cancelar
            </Button>
            <Button type="submit" disabled={mutation.isPending}>
              {editing ? 'Salvar' : 'Criar usuário'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
