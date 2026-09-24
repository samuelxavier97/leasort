import { useFieldArray, useFormContext } from 'react-hook-form'
import { FieldError } from '@/components/FieldError'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { formatCpfInput } from '@/lib/cpf'
import { operationToday } from '@/lib/date'
import { emptyCompanion, type CompanionForm } from './companionForm'
import { RELATIONSHIP_LABELS } from './labels'

/**
 * Lista editável de acompanhantes (nome, parentesco, nascimento e CPF opcional). O limite de
 * acompanhantes é do backend (APP_MAX_COMPANIONS) e volta como erro com o número (D-075).
 */
export function CompanionFields() {
  const {
    control,
    register,
    setValue,
    formState: { errors },
  } = useFormContext<{ companions: CompanionForm[] }>()
  const { fields, append, remove } = useFieldArray({ control, name: 'companions' })

  return (
    <div className="space-y-4">
      {fields.length === 0 && <p className="text-sm text-muted-foreground">Nenhum acompanhante.</p>}
      {fields.map((field, index) => {
        const number = index + 1
        const fieldErrors = errors.companions?.[index]
        const prefix = `companion-${index}`
        return (
          <div key={field.id} role="group" aria-label={`Acompanhante ${number}`} className="space-y-3 rounded-md border p-3">
            <div className="flex items-center justify-between">
              <p className="text-sm font-medium">Acompanhante {number}</p>
              <Button
                type="button"
                variant="ghost"
                size="sm"
                aria-label={`Remover acompanhante ${number}`}
                onClick={() => remove(index)}
              >
                Remover
              </Button>
            </div>
            <div className="space-y-2">
              <Label htmlFor={`${prefix}-name`}>Nome do acompanhante {number}</Label>
              <Input
                id={`${prefix}-name`}
                aria-invalid={!!fieldErrors?.name}
                aria-describedby={`${prefix}-name-error`}
                {...register(`companions.${index}.name`)}
              />
              <FieldError id={`${prefix}-name-error`} message={fieldErrors?.name?.message} />
            </div>
            <div className="grid gap-3 sm:grid-cols-2">
              <div className="space-y-2">
                <Label htmlFor={`${prefix}-relationship`}>Parentesco do acompanhante {number}</Label>
                <select
                  id={`${prefix}-relationship`}
                  className="h-9 w-full rounded-md border border-input bg-transparent px-3 text-sm"
                  aria-invalid={!!fieldErrors?.relationship}
                  aria-describedby={`${prefix}-relationship-error`}
                  {...register(`companions.${index}.relationship`)}
                >
                  <option value="">Selecione</option>
                  {Object.entries(RELATIONSHIP_LABELS).map(([value, label]) => (
                    <option key={value} value={value}>
                      {label}
                    </option>
                  ))}
                </select>
                <FieldError id={`${prefix}-relationship-error`} message={fieldErrors?.relationship?.message} />
              </div>
              <div className="space-y-2">
                <Label htmlFor={`${prefix}-birth`}>Nascimento do acompanhante {number}</Label>
                <Input
                  id={`${prefix}-birth`}
                  type="date"
                  min="1900-01-01"
                  max={operationToday()}
                  aria-invalid={!!fieldErrors?.birthDate}
                  aria-describedby={`${prefix}-birth-error`}
                  {...register(`companions.${index}.birthDate`)}
                />
                <FieldError id={`${prefix}-birth-error`} message={fieldErrors?.birthDate?.message} />
              </div>
            </div>
            <div className="space-y-2">
              <Label htmlFor={`${prefix}-cpf`}>CPF do acompanhante {number} (opcional)</Label>
              {field.lockedCpf !== null ? (
                <>
                  <Input id={`${prefix}-cpf`} value={field.lockedCpf} readOnly disabled />
                  <p className="text-sm text-muted-foreground">Só o administrador altera o CPF.</p>
                </>
              ) : (
                <>
                  <Input
                    id={`${prefix}-cpf`}
                    inputMode="numeric"
                    placeholder="000.000.000-00"
                    aria-invalid={!!fieldErrors?.cpf}
                    aria-describedby={`${prefix}-cpf-error`}
                    {...register(`companions.${index}.cpf`)}
                    onChange={(event) =>
                      setValue(`companions.${index}.cpf`, formatCpfInput(event.target.value), { shouldDirty: true })
                    }
                  />
                  <FieldError id={`${prefix}-cpf-error`} message={fieldErrors?.cpf?.message} />
                </>
              )}
            </div>
          </div>
        )
      })}
      <Button type="button" variant="outline" onClick={() => append(emptyCompanion())}>
        Adicionar acompanhante
      </Button>
    </div>
  )
}
