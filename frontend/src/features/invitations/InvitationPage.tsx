import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { toast } from 'sonner'
import { ConfirmDialog } from '@/components/ConfirmDialog'
import { FormAlert } from '@/components/FormAlert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { leadsQueryKey } from '@/features/leads/api'
import { cancelVisit, visitsQueryKey } from '@/features/visits/api'
import { errorMessage } from '@/lib/errors'
import { formatDate } from '@/lib/format'
import { getInvitation, getQrCode, invitationsQueryKey, reissueInvitation, type Invitation } from './api'
import { INVITATION_STATUS_LABELS } from './labels'
import { deliverImage, renderShareImage, shareImageContent } from './shareImage'

/** Tela do convite (§16.4): dados da visita, QR, código e ações. */
export function InvitationPage() {
  const { id = '' } = useParams()
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const [confirmReissue, setConfirmReissue] = useState(false)
  const [confirmCancel, setConfirmCancel] = useState(false)
  const [delivering, setDelivering] = useState(false)

  const detailKey = [...invitationsQueryKey, 'detail', id]
  const invitation = useQuery({ queryKey: detailKey, queryFn: () => getInvitation(id) })
  const active = invitation.data?.status === 'ACTIVE'
  const qr = useQuery({
    queryKey: [...invitationsQueryKey, 'qr', id],
    queryFn: () => getQrCode(id),
    enabled: active,
    staleTime: Infinity,
  })
  const qrUrl = useMemo(() => (qr.data ? URL.createObjectURL(qr.data) : null), [qr.data])
  useEffect(() => () => {
    if (qrUrl) URL.revokeObjectURL(qrUrl)
  }, [qrUrl])

  const refreshRelated = () => {
    queryClient.invalidateQueries({ queryKey: invitationsQueryKey, predicate: (query) => query.queryKey[1] !== 'detail' })
    queryClient.invalidateQueries({ queryKey: visitsQueryKey })
    queryClient.invalidateQueries({ queryKey: leadsQueryKey })
  }

  const reissue = useMutation({
    mutationFn: () => reissueInvitation(id),
    onSuccess: (created) => {
      setConfirmReissue(false)
      queryClient.setQueryData([...invitationsQueryKey, 'detail', created.id], created)
      queryClient.invalidateQueries({ queryKey: detailKey })
      refreshRelated()
      toast.success('Novo código gerado. O código anterior deixou de valer.')
      navigate(`/convites/${created.id}`)
    },
    onError: () => setConfirmReissue(false),
  })

  const cancel = useMutation({
    mutationFn: (data: Invitation) => cancelVisit(data.visit.id),
    onSuccess: async () => {
      setConfirmCancel(false)
      await queryClient.invalidateQueries({ queryKey: detailKey })
      refreshRelated()
      toast.success('Visita cancelada.')
    },
    onError: () => setConfirmCancel(false),
  })

  async function deliver(data: Invitation, mode: 'share' | 'download') {
    if (!qr.data) return
    setDelivering(true)
    try {
      const content = shareImageContent(data)
      await deliverImage(await renderShareImage(content, qr.data), content, mode)
    } catch {
      toast.error('Não foi possível gerar a imagem do convite.')
    } finally {
      setDelivering(false)
    }
  }

  if (invitation.isPending) {
    return <p className="text-muted-foreground">Carregando...</p>
  }
  if (invitation.isError) {
    return <p className="text-destructive">{errorMessage(invitation.error)}</p>
  }

  const data = invitation.data
  const mutationError = reissue.error ?? cancel.error

  return (
    <section className="max-w-xl space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h1 className="text-2xl font-semibold">Convite</h1>
        <Badge variant={active ? 'secondary' : 'outline'}>{INVITATION_STATUS_LABELS[data.status]}</Badge>
      </div>

      <FormAlert message={mutationError ? errorMessage(mutationError) : null} />

      <Card>
        <CardContent className="space-y-4 pt-6">
          <dl className="grid grid-cols-[auto_1fr] gap-x-6 gap-y-2 text-sm">
            <dt className="text-muted-foreground">Lead</dt>
            <dd>
              {data.lead.accessible ? (
                <Link className="underline" to={`/leads/${data.lead.id}`}>
                  {data.lead.name}
                </Link>
              ) : (
                data.lead.name
              )}
            </dd>
            <dt className="text-muted-foreground">Data da visita</dt>
            <dd>{formatDate(data.visit.scheduledDate)}</dd>
            <dt className="text-muted-foreground">Prospector</dt>
            <dd>{data.prospector.name}</dd>
            <dt className="text-muted-foreground">Acompanhantes</dt>
            <dd>{data.visit.companionsCount}</dd>
            <dt className="text-muted-foreground">Status</dt>
            <dd>{INVITATION_STATUS_LABELS[data.status]}</dd>
          </dl>

          {active ? (
            <div className="flex flex-col items-center gap-3">
              {qrUrl ? (
                <img src={qrUrl} alt="QR Code do convite" className="size-64 max-w-full" />
              ) : (
                <div className="flex size-64 items-center justify-center text-sm text-muted-foreground">
                  {qr.isError ? 'QR indisponível.' : 'Carregando QR...'}
                </div>
              )}
              <p className="font-mono text-3xl font-bold tracking-widest" data-testid="invitation-code">
                {data.formattedCode}
              </p>
            </div>
          ) : (
            <p className="rounded-md border p-3 text-sm text-muted-foreground">
              Este convite não é mais válido e não pode ser compartilhado.
            </p>
          )}
        </CardContent>
      </Card>

      <div className="flex flex-wrap gap-2">
        {active && (
          <>
            <Button onClick={() => deliver(data, 'share')} disabled={!qr.data || delivering}>
              Compartilhar
            </Button>
            <Button variant="outline" onClick={() => deliver(data, 'download')} disabled={!qr.data || delivering}>
              Baixar
            </Button>
          </>
        )}
        {data.canReissue && (
          <Button variant="outline" onClick={() => setConfirmReissue(true)} disabled={reissue.isPending}>
            Reemitir código
          </Button>
        )}
        {/* Convite reemitido ou cancelado não oferece ações da visita: a visita segue pelo convite atual. */}
        {active && data.visit.canEdit && (
          <Button variant="outline" onClick={() => setConfirmCancel(true)} disabled={cancel.isPending}>
            Cancelar visita
          </Button>
        )}
        <Button variant="ghost" asChild>
          <Link to={`/visitas/${data.visit.id}`}>Ver visita</Link>
        </Button>
      </div>

      <ConfirmDialog
        open={confirmReissue}
        title="Reemitir código"
        description="O código atual deixará de valer. Um novo código será gerado para a mesma visita e precisará ser compartilhado de novo."
        confirmLabel="Reemitir"
        pending={reissue.isPending}
        onConfirm={() => reissue.mutate()}
        onCancel={() => setConfirmReissue(false)}
      />
      <ConfirmDialog
        open={confirmCancel}
        title="Cancelar visita"
        description={`A visita de ${formatDate(data.visit.scheduledDate)} e este convite serão cancelados, e o Lead volta para "Contatado".`}
        confirmLabel="Confirmar cancelamento"
        pending={cancel.isPending}
        onConfirm={() => cancel.mutate(data)}
        onCancel={() => setConfirmCancel(false)}
      />
    </section>
  )
}
