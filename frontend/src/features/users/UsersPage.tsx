import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { toast } from 'sonner'
import { ROLE_LABELS } from '@/app/navigation'
import { ConfirmDialog } from '@/components/ConfirmDialog'
import { Pagination } from '@/components/Pagination'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { errorMessage } from '@/lib/errors'
import { changeUserStatus, listUsers, resetUserPassword, usersQueryKey, type User } from './api'
import { TemporaryPasswordDialog } from './TemporaryPasswordDialog'
import { UserFormDialog } from './UserFormDialog'

type PendingAction = { kind: 'status' | 'reset'; user: User }

export function UsersPage() {
  const queryClient = useQueryClient()
  const [page, setPage] = useState(0)
  const [form, setForm] = useState<{ open: boolean; user: User | null }>({ open: false, user: null })
  const [pending, setPending] = useState<PendingAction | null>(null)
  const [temporary, setTemporary] = useState<{ userName: string; password: string } | null>(null)

  const users = useQuery({
    queryKey: [...usersQueryKey, page],
    queryFn: () => listUsers(page),
    placeholderData: keepPreviousData,
  })

  const refresh = () => queryClient.invalidateQueries({ queryKey: usersQueryKey })

  const action = useMutation({
    mutationFn: async ({ kind, user }: PendingAction) => {
      if (kind === 'status') {
        await changeUserStatus(user.id, !user.active)
        return null
      }
      return (await resetUserPassword(user.id)).temporaryPassword
    },
    onSuccess: (temporaryPassword, { kind, user }) => {
      setPending(null)
      if (kind === 'reset' && temporaryPassword) {
        setTemporary({ userName: user.name, password: temporaryPassword })
      } else {
        toast.success(user.active ? 'Usuário desativado.' : 'Usuário ativado.')
      }
      refresh()
    },
    onError: (error) => {
      setPending(null)
      toast.error(errorMessage(error))
    },
  })

  return (
    <section className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h1 className="text-2xl font-semibold">Usuários</h1>
        <Button onClick={() => setForm({ open: true, user: null })}>Novo usuário</Button>
      </div>

      {users.isError && <p className="text-destructive">{errorMessage(users.error)}</p>}
      {users.data && (
        <div className="overflow-x-auto rounded-md border bg-background">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Nome</TableHead>
                <TableHead>E-mail</TableHead>
                <TableHead>Perfil</TableHead>
                <TableHead>Situação</TableHead>
                <TableHead className="text-right">Ações</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {users.data.content.map((user) => (
                <TableRow key={user.id}>
                  <TableCell>{user.name}</TableCell>
                  <TableCell>{user.email}</TableCell>
                  <TableCell>
                    {ROLE_LABELS[user.role]}
                    {user.employeeCode && <span className="ml-1 text-muted-foreground">({user.employeeCode})</span>}
                  </TableCell>
                  <TableCell>
                    <Badge variant={user.active ? 'secondary' : 'outline'}>{user.active ? 'Ativo' : 'Inativo'}</Badge>
                  </TableCell>
                  <TableCell>
                    <div className="flex flex-wrap justify-end gap-1">
                      <Button variant="ghost" size="sm" onClick={() => setForm({ open: true, user })}>
                        Editar
                      </Button>
                      <Button variant="ghost" size="sm" onClick={() => setPending({ kind: 'status', user })}>
                        {user.active ? 'Desativar' : 'Ativar'}
                      </Button>
                      <Button variant="ghost" size="sm" onClick={() => setPending({ kind: 'reset', user })}>
                        Redefinir senha
                      </Button>
                    </div>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}
      {users.data && <Pagination page={users.data.page} totalPages={users.data.totalPages} onChange={setPage} />}

      <UserFormDialog
        open={form.open}
        user={form.user}
        onClose={() => setForm({ open: false, user: null })}
        onCreated={(user, password) => {
          setForm({ open: false, user: null })
          setTemporary({ userName: user.name, password })
          refresh()
        }}
        onUpdated={() => {
          setForm({ open: false, user: null })
          toast.success('Usuário atualizado.')
          refresh()
        }}
      />

      <ConfirmDialog
        open={pending !== null}
        title={
          pending?.kind === 'reset'
            ? 'Redefinir senha'
            : pending?.user.active
              ? 'Desativar usuário'
              : 'Ativar usuário'
        }
        description={
          pending?.kind === 'reset'
            ? `Uma senha temporária será gerada para ${pending.user.name}, e as sessões abertas serão encerradas.`
            : pending?.user.active
              ? `${pending.user.name} perderá o acesso, e as sessões abertas serão encerradas.`
              : `${pending?.user.name ?? ''} voltará a ter acesso.`
        }
        confirmLabel="Confirmar"
        pending={action.isPending}
        onConfirm={() => pending && action.mutate(pending)}
        onCancel={() => setPending(null)}
      />

      <TemporaryPasswordDialog
        userName={temporary?.userName ?? ''}
        password={temporary?.password ?? null}
        onClose={() => setTemporary(null)}
      />
    </section>
  )
}
