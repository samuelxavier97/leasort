import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { toast } from 'sonner'
import { FormAlert } from '@/components/FormAlert'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { RELATIONSHIP_LABELS } from '@/features/visits/labels'
import { errorMessage } from '@/lib/errors'
import { formatDate } from '@/lib/format'
import { ACCESS_CODE_LENGTH, cleanAccessCode, formatAccessCode } from './accessCode'
import { recentAccessQueryKey, registerAccess, validateAccess, type AccessResponse, type DenialReason } from './api'
import { CAMERA_PROBLEM_MESSAGES, DENIAL_REASON_LABELS, denialMessage, type CameraProblem } from './labels'
import { QrScanner } from './QrScanner'

type View =
  | { kind: 'start' }
  | { kind: 'scan' }
  | { kind: 'type' }
  | { kind: 'authorized'; access: AccessResponse }
  | { kind: 'denied'; reason: DenialReason; scheduledDate?: string }

const BIG = 'h-16 w-full text-lg font-semibold'

/** Portaria (§16.5): validar por QR ou código digitado, conferir e registrar a entrada. */
export function GatePage() {
  const queryClient = useQueryClient()
  const [view, setView] = useState<View>({ kind: 'start' })
  const [cameraProblem, setCameraProblem] = useState<CameraProblem | null>(null)
  const [code, setCode] = useState('')
  const [present, setPresent] = useState<Set<string>>(new Set())

  /** Após confirmar ou negar, volta ao scanner; sem câmera, à digitação com o campo limpo. */
  function restart() {
    setCode('')
    validate.reset()
    register.reset()
    setView(cameraProblem ? { kind: 'type' } : { kind: 'scan' })
  }

  function show(access: AccessResponse) {
    if (access.result === 'AUTHORIZED') {
      setPresent(new Set(access.companions?.map((companion) => companion.id)))
      setView({ kind: 'authorized', access })
    } else {
      setView({ kind: 'denied', reason: access.denialReason ?? 'INVALID_CODE', scheduledDate: access.scheduledDate })
      queryClient.invalidateQueries({ queryKey: recentAccessQueryKey })
    }
  }

  const validate = useMutation({
    mutationFn: ({ text }: { text: string; source: View }) => validateAccess(text),
    onSuccess: (access) => show(access),
    // Sem voltar direto ao scanner: o mesmo QR diante da câmera repetiria o erro.
    onError: (_error, { source }) => setView(source.kind === 'scan' ? { kind: 'start' } : source),
  })

  const register = useMutation({
    mutationFn: ({ invitationId, ids }: { invitationId: string; ids: string[] }) => registerAccess(invitationId, ids),
    onSuccess: (access) => {
      queryClient.invalidateQueries({ queryKey: recentAccessQueryKey })
      if (access.result === 'AUTHORIZED') {
        toast.success(`Entrada de ${access.leadName} registrada.`)
        restart()
      } else {
        // Negativa na confirmação (ex.: clique duplo → ALREADY_USED) cai na mesma tela de negado.
        show(access)
      }
    },
  })

  function onCameraProblem(problem: CameraProblem) {
    setCameraProblem(problem)
    setView({ kind: 'type' })
  }

  function submitCode(event: FormEvent) {
    event.preventDefault()
    if (code.length === ACCESS_CODE_LENGTH) {
      validate.mutate({ text: code, source: { kind: 'type' } })
    }
  }

  function toggle(id: string, checked: boolean) {
    setPresent((current) => {
      const next = new Set(current)
      if (checked) next.add(id)
      else next.delete(id)
      return next
    })
  }

  const error = validate.error ?? register.error

  if (view.kind === 'authorized') {
    const { access } = view
    const companions = access.companions ?? []
    return (
      <section className="mx-auto max-w-md space-y-4">
        <h1 className="rounded-md bg-green-600 p-4 text-center text-2xl font-bold text-white">ACESSO LIBERADO</h1>
        <FormAlert message={register.error ? errorMessage(register.error) : null} />
        <Card>
          <CardContent className="space-y-4 pt-6">
            <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-2">
              <dt className="text-muted-foreground">Nome</dt>
              <dd className="min-w-0 break-words font-semibold">{access.leadName}</dd>
              <dt className="text-muted-foreground">Data</dt>
              <dd>{formatDate(access.scheduledDate)}</dd>
              <dt className="text-muted-foreground">Prospector</dt>
              <dd className="min-w-0 break-words">{access.prospectorName}</dd>
            </dl>
            <fieldset className="space-y-2">
              <legend className="mb-2 font-medium">Acompanhantes</legend>
              {companions.length === 0 ? (
                <p className="text-sm text-muted-foreground">Visita sem acompanhantes.</p>
              ) : (
                <>
                  <p className="text-sm text-muted-foreground">Desmarque quem não veio.</p>
                  {companions.map((companion) => (
                    <label key={companion.id} className="flex min-h-12 items-center gap-3 rounded-md border px-3">
                      <input
                        type="checkbox"
                        className="size-5 shrink-0"
                        checked={present.has(companion.id)}
                        onChange={(event) => toggle(companion.id, event.target.checked)}
                      />
                      <span className="min-w-0 break-words">
                        {companion.name}{' '}
                        <span className="text-sm text-muted-foreground">({RELATIONSHIP_LABELS[companion.relationship]})</span>
                      </span>
                    </label>
                  ))}
                </>
              )}
            </fieldset>
          </CardContent>
        </Card>
        <Button
          className={BIG}
          disabled={register.isPending}
          onClick={() =>
            register.mutate({
              invitationId: access.invitationId ?? '',
              // Na ordem da visita, só os marcados.
              ids: companions.filter((companion) => present.has(companion.id)).map((companion) => companion.id),
            })
          }
        >
          CONFIRMAR ENTRADA
        </Button>
        <Button variant="ghost" className="w-full" disabled={register.isPending} onClick={restart}>
          Cancelar
        </Button>
      </section>
    )
  }

  if (view.kind === 'denied') {
    return (
      <section className="mx-auto max-w-md space-y-4">
        <h1 className="rounded-md bg-destructive p-4 text-center text-2xl font-bold text-white">ACESSO NEGADO</h1>
        <Card>
          <CardContent className="space-y-2 pt-6">
            <p className="text-xl font-semibold">{DENIAL_REASON_LABELS[view.reason]}</p>
            <p>{denialMessage(view.reason, view.scheduledDate)}</p>
          </CardContent>
        </Card>
        <Button className={BIG} onClick={restart}>
          NOVA VALIDAÇÃO
        </Button>
      </section>
    )
  }

  return (
    <section className="mx-auto max-w-md space-y-4">
      <h1 className="text-center text-2xl font-semibold">Validar Convite</h1>
      <FormAlert message={error ? errorMessage(error) : null} />
      {cameraProblem && (
        <p role="status" className="rounded-md border border-amber-300 bg-amber-50 p-3 text-sm text-amber-900">
          {CAMERA_PROBLEM_MESSAGES[cameraProblem]}
        </p>
      )}

      {view.kind === 'scan' ? (
        <div className="space-y-2">
          <QrScanner
            onRead={(text) => validate.mutate({ text, source: { kind: 'scan' } })}
            onProblem={onCameraProblem}
          />
          <p className="text-center text-sm text-muted-foreground">
            {validate.isPending ? 'Validando...' : 'Aponte a câmera para o QR Code do convite.'}
          </p>
        </div>
      ) : (
        !cameraProblem && (
          <Button className={BIG} onClick={() => setView({ kind: 'scan' })}>
            ESCANEAR QR CODE
          </Button>
        )
      )}

      {view.kind === 'type' ? (
        <form className="space-y-3" onSubmit={submitCode}>
          <div className="space-y-1">
            <Label htmlFor="access-code">Código do convite</Label>
            <Input
              id="access-code"
              className="h-14 text-center font-mono text-2xl tracking-widest md:text-2xl"
              value={formatAccessCode(code)}
              onChange={(event) => setCode(cleanAccessCode(event.target.value))}
              placeholder="XXXXX-XXXXX"
              autoCapitalize="characters"
              autoComplete="off"
              autoCorrect="off"
              spellCheck={false}
              autoFocus
            />
          </div>
          <Button type="submit" className={BIG} disabled={code.length !== ACCESS_CODE_LENGTH || validate.isPending}>
            {validate.isPending ? 'Validando...' : 'VALIDAR'}
          </Button>
        </form>
      ) : (
        <>
          {view.kind === 'start' && <p className="text-center text-muted-foreground">ou</p>}
          <Button variant="outline" className={BIG} onClick={() => setView({ kind: 'type' })}>
            DIGITAR CÓDIGO
          </Button>
        </>
      )}
    </section>
  )
}
