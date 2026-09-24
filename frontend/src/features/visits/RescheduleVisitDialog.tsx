import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation } from '@tanstack/react-query'
import { useEffect } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { FieldError } from '@/components/FieldError'
import { FormAlert } from '@/components/FormAlert'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { errorMessage } from '@/lib/errors'
import { formatDate } from '@/lib/format'
import { rescheduleVisit, type Visit } from './api'
import { visitDateLimits, visitDateSchema } from './visitDate'

/** Remarcação (D-076): nova data diferente da atual, de hoje até hoje + 12 meses. */
export function RescheduleVisitDialog({
  open,
  visit,
  onClose,
  onRescheduled,
}: {
  open: boolean
  visit: Visit
  onClose: () => void
  onRescheduled: (visit: Visit) => void
}) {
  const schema = z.object({
    scheduledDate: visitDateSchema.refine(
      (value) => value !== visit.scheduledDate,
      'Escolha uma data diferente da atual.',
    ),
  })
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<{ scheduledDate: string }>({ resolver: zodResolver(schema) })
  const limits = visitDateLimits()

  useEffect(() => {
    if (open) reset({ scheduledDate: '' })
  }, [open, reset])

  const mutation = useMutation({
    mutationFn: ({ scheduledDate }: { scheduledDate: string }) => rescheduleVisit(visit.id, scheduledDate),
    onSuccess: onRescheduled,
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
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Remarcar visita</DialogTitle>
          <DialogDescription>
            Data atual: {formatDate(visit.scheduledDate)}. A visita atual será cancelada e uma nova será criada com os
            mesmos acompanhantes e observações.
          </DialogDescription>
        </DialogHeader>
        <form className="min-w-0 space-y-4" noValidate onSubmit={handleSubmit((values) => mutation.mutate(values))}>
          <FormAlert message={mutation.isError ? errorMessage(mutation.error) : null} />
          <div className="space-y-2">
            <Label htmlFor="reschedule-date">Nova data</Label>
            <Input
              id="reschedule-date"
              type="date"
              min={limits.min}
              max={limits.max}
              aria-invalid={!!errors.scheduledDate}
              aria-describedby="reschedule-date-error"
              {...register('scheduledDate')}
            />
            <FieldError id="reschedule-date-error" message={errors.scheduledDate?.message} />
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose}>
              Cancelar
            </Button>
            <Button type="submit" disabled={mutation.isPending}>
              Remarcar
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
