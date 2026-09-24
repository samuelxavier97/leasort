import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { Link, useNavigate } from 'react-router'
import { toast } from 'sonner'
import { z } from 'zod'
import { landingPath } from '@/app/navigation'
import { FieldError } from '@/components/FieldError'
import { FormAlert } from '@/components/FormAlert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { errorMessage } from '@/lib/errors'
import { changePassword, meQueryKey } from './api'
import { newPasswordSchema } from './passwordSchema'
import type { Me } from './types'
import { useMe } from './useMe'

const schema = z
  .object({
    currentPassword: z.string().min(1, 'Informe a senha atual.'),
    newPassword: newPasswordSchema,
    confirmPassword: z.string(),
  })
  .refine((form) => form.newPassword === form.confirmPassword, {
    path: ['confirmPassword'],
    message: 'As senhas não conferem.',
  })

type ChangePasswordForm = z.infer<typeof schema>

export function ChangePasswordPage() {
  const { data: me } = useMe()
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<ChangePasswordForm>({
    resolver: zodResolver(schema),
    defaultValues: { currentPassword: '', newPassword: '', confirmPassword: '' },
  })

  const mutation = useMutation({
    mutationFn: (form: ChangePasswordForm) => changePassword(form.currentPassword, form.newPassword),
    onSuccess: () => {
      if (!me) {
        return
      }
      queryClient.setQueryData<Me>(meQueryKey, { ...me, mustChangePassword: false })
      toast.success('Senha alterada.')
      navigate(landingPath(me.role), { replace: true })
    },
  })

  if (!me) {
    return null
  }

  return (
    <main className="flex min-h-svh items-center justify-center bg-muted/40 p-4">
      <Card className="w-full max-w-sm">
        <CardHeader>
          <CardTitle>
            <h1 className="text-xl">Trocar senha</h1>
          </CardTitle>
          {me.mustChangePassword && (
            <CardDescription>Defina uma nova senha para continuar usando o sistema.</CardDescription>
          )}
        </CardHeader>
        <CardContent>
          <form className="space-y-4" noValidate onSubmit={handleSubmit((form) => mutation.mutate(form))}>
            <FormAlert message={mutation.isError ? errorMessage(mutation.error) : null} />
            <div className="space-y-2">
              <Label htmlFor="currentPassword">Senha atual</Label>
              <Input
                id="currentPassword"
                type="password"
                autoComplete="current-password"
                aria-invalid={!!errors.currentPassword}
                aria-describedby="currentPassword-error"
                {...register('currentPassword')}
              />
              <FieldError id="currentPassword-error" message={errors.currentPassword?.message} />
            </div>
            <div className="space-y-2">
              <Label htmlFor="newPassword">Nova senha</Label>
              <Input
                id="newPassword"
                type="password"
                autoComplete="new-password"
                aria-invalid={!!errors.newPassword}
                aria-describedby="newPassword-error"
                {...register('newPassword')}
              />
              <FieldError id="newPassword-error" message={errors.newPassword?.message} />
            </div>
            <div className="space-y-2">
              <Label htmlFor="confirmPassword">Confirme a nova senha</Label>
              <Input
                id="confirmPassword"
                type="password"
                autoComplete="new-password"
                aria-invalid={!!errors.confirmPassword}
                aria-describedby="confirmPassword-error"
                {...register('confirmPassword')}
              />
              <FieldError id="confirmPassword-error" message={errors.confirmPassword?.message} />
            </div>
            <Button type="submit" className="w-full" disabled={mutation.isPending}>
              {mutation.isPending ? 'Salvando...' : 'Salvar nova senha'}
            </Button>
            {!me.mustChangePassword && (
              <Button asChild variant="ghost" className="w-full">
                <Link to={landingPath(me.role)}>Cancelar</Link>
              </Button>
            )}
          </form>
        </CardContent>
      </Card>
    </main>
  )
}
