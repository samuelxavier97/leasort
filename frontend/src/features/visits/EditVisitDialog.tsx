import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation } from '@tanstack/react-query'
import { useEffect } from 'react'
import { FormProvider, useForm } from 'react-hook-form'
import { z } from 'zod'
import { FieldError } from '@/components/FieldError'
import { FormAlert } from '@/components/FormAlert'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Label } from '@/components/ui/label'
import type { Role } from '@/features/auth/types'
import { errorMessage } from '@/lib/errors'
import { updateVisit, type Visit } from './api'
import { CompanionFields } from './CompanionFields'
import { companionFromVisit, companionInput, companionSchema } from './companionForm'

const schema = z.object({
  notes: z.string().max(2000, 'Máximo de 2000 caracteres.'),
  hostNotes: z.string().max(2000, 'Máximo de 2000 caracteres.'),
  companions: z.array(companionSchema),
})

type EditForm = z.infer<typeof schema>

/**
 * Edição de observações e acompanhantes. A lista enviada substitui a atual: existentes vão com `id`,
 * novos sem `id`, removidos ficam de fora (D-075).
 */
export function EditVisitDialog({
  open,
  visit,
  role,
  onClose,
  onSaved,
}: {
  open: boolean
  visit: Visit
  role: Role
  onClose: () => void
  onSaved: (visit: Visit) => void
}) {
  const form = useForm<EditForm>({ resolver: zodResolver(schema) })
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = form

  useEffect(() => {
    if (open) {
      reset({
        notes: visit.notes ?? '',
        hostNotes: visit.hostNotes ?? '',
        companions: visit.companions.map((companion) => companionFromVisit(companion, role)),
      })
    }
  }, [open, visit, role, reset])

  const mutation = useMutation({
    mutationFn: (values: EditForm) =>
      updateVisit(visit.id, {
        notes: values.notes.trim() || null,
        hostNotes: values.hostNotes.trim() || null,
        companions: values.companions.map(companionInput),
      }),
    onSuccess: onSaved,
  })

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
          <DialogTitle>Editar visita</DialogTitle>
        </DialogHeader>
        <FormProvider {...form}>
          <form className="min-w-0 space-y-4" noValidate onSubmit={handleSubmit((values) => mutation.mutate(values))}>
            <FormAlert message={mutation.isError ? errorMessage(mutation.error) : null} />
            <div className="space-y-2">
              <Label htmlFor="visit-notes">Observações internas</Label>
              <textarea
                id="visit-notes"
                rows={2}
                className="w-full rounded-md border border-input bg-transparent px-3 py-2 text-sm"
                aria-invalid={!!errors.notes}
                aria-describedby="visit-notes-error"
                {...register('notes')}
              />
              <FieldError id="visit-notes-error" message={errors.notes?.message} />
            </div>
            <div className="space-y-2">
              <Label htmlFor="visit-host-notes">Observações para o anfitrião</Label>
              <textarea
                id="visit-host-notes"
                rows={2}
                className="w-full rounded-md border border-input bg-transparent px-3 py-2 text-sm"
                aria-invalid={!!errors.hostNotes}
                aria-describedby="visit-host-notes-error"
                {...register('hostNotes')}
              />
              <FieldError id="visit-host-notes-error" message={errors.hostNotes?.message} />
            </div>
            <div className="space-y-2">
              <p className="text-sm font-medium">Acompanhantes</p>
              <CompanionFields />
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
        </FormProvider>
      </DialogContent>
    </Dialog>
  )
}
