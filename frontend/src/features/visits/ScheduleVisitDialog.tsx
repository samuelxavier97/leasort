import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { FormProvider, useForm, useWatch, type FieldErrors } from 'react-hook-form'
import { z } from 'zod'
import { CollapsibleSection } from '@/components/CollapsibleSection'
import { FieldError } from '@/components/FieldError'
import { FormAlert } from '@/components/FormAlert'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { errorMessage } from '@/lib/errors'
import { createVisit, type Visit } from './api'
import { CompanionFields } from './CompanionFields'
import { companionInput, companionSchema } from './companionForm'
import { visitDateSchema, visitDateLimits } from './visitDate'

const schema = z.object({
  scheduledDate: visitDateSchema,
  hostNotes: z.string().max(2000, 'Máximo de 2000 caracteres.'),
  companions: z.array(companionSchema),
})

type ScheduleForm = z.infer<typeof schema>

/** Agendamento (SPEC §16.3): o fluxo mínimo é data e confirmar; as seções opcionais começam recolhidas. */
export function ScheduleVisitDialog({
  open,
  lead,
  onClose,
  onScheduled,
}: {
  open: boolean
  lead: { id: string; name: string }
  onClose: () => void
  onScheduled: (visit: Visit) => void
}) {
  const form = useForm<ScheduleForm>({ resolver: zodResolver(schema) })
  const {
    register,
    handleSubmit,
    reset,
    control,
    formState: { errors },
  } = form
  const [companionsOpen, setCompanionsOpen] = useState(false)
  const [hostNotesOpen, setHostNotesOpen] = useState(false)
  const limits = visitDateLimits()

  useEffect(() => {
    if (open) reset({ scheduledDate: '', hostNotes: '', companions: [] })
  }, [open, reset])

  const mutation = useMutation({
    mutationFn: (values: ScheduleForm) =>
      createVisit({
        leadId: lead.id,
        scheduledDate: values.scheduledDate,
        notes: null,
        hostNotes: values.hostNotes.trim() || null,
        companions: values.companions.map(companionInput),
      }),
    onSuccess: onScheduled,
  })

  const close = () => {
    mutation.reset()
    setCompanionsOpen(false)
    setHostNotesOpen(false)
    onClose()
  }

  /** Erro de validação numa seção recolhida: abre a seção para o erro ficar visível. */
  const onInvalid = (invalid: FieldErrors<ScheduleForm>) => {
    if (invalid.companions) setCompanionsOpen(true)
    if (invalid.hostNotes) setHostNotesOpen(true)
  }

  const companionCount = useWatch({ control, name: 'companions' })?.length ?? 0

  return (
    <Dialog
      open={open}
      onOpenChange={(value) => {
        if (!value) close()
      }}
    >
      <DialogContent className="max-h-[90svh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>Agendar visita</DialogTitle>
          <DialogDescription>Lead: {lead.name}</DialogDescription>
        </DialogHeader>
        <FormProvider {...form}>
          <form className="min-w-0 space-y-4" noValidate onSubmit={handleSubmit((values) => mutation.mutate(values), onInvalid)}>
            <FormAlert message={mutation.isError ? errorMessage(mutation.error) : null} />
            <div className="space-y-2">
              <Label htmlFor="visit-date">Data da visita</Label>
              <Input
                id="visit-date"
                type="date"
                min={limits.min}
                max={limits.max}
                aria-invalid={!!errors.scheduledDate}
                aria-describedby="visit-date-error"
                {...register('scheduledDate')}
              />
              <FieldError id="visit-date-error" message={errors.scheduledDate?.message} />
            </div>
            <CollapsibleSection
              title={companionCount > 0 ? `Acompanhantes (${companionCount})` : 'Acompanhantes (opcional)'}
              open={companionsOpen}
              onToggle={() => setCompanionsOpen((value) => !value)}
            >
              <CompanionFields />
            </CollapsibleSection>
            <CollapsibleSection
              title="Observações para o anfitrião (opcional)"
              open={hostNotesOpen}
              onToggle={() => setHostNotesOpen((value) => !value)}
            >
              <div className="space-y-2">
                <Label htmlFor="visit-host-notes">Observações para o anfitrião</Label>
                <textarea
                  id="visit-host-notes"
                  rows={3}
                  className="w-full rounded-md border border-input bg-transparent px-3 py-2 text-sm"
                  aria-invalid={!!errors.hostNotes}
                  aria-describedby="visit-host-notes-error"
                  {...register('hostNotes')}
                />
                <FieldError id="visit-host-notes-error" message={errors.hostNotes?.message} />
              </div>
            </CollapsibleSection>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={close}>
                Cancelar
              </Button>
              <Button type="submit" disabled={mutation.isPending}>
                Confirmar agendamento
              </Button>
            </DialogFooter>
          </form>
        </FormProvider>
      </DialogContent>
    </Dialog>
  )
}
