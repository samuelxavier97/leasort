import { Navigate, Outlet, useLocation } from 'react-router'
import type { Role } from '@/features/auth/types'
import { useMe } from '@/features/auth/useMe'
import { landingPath } from './navigation'

/** Exige sessão. Com troca de senha pendente, só a tela de troca é acessível (D-055). */
export function RequireAuth() {
  const { data: me, isPending, isError } = useMe()
  const location = useLocation()

  if (isPending) {
    return <p className="p-6 text-muted-foreground">Carregando...</p>
  }
  if (isError) {
    return <p className="p-6 text-destructive">Não foi possível conectar ao servidor.</p>
  }
  if (!me) {
    return <Navigate to="/login" replace />
  }
  if (me.mustChangePassword && location.pathname !== '/trocar-senha') {
    return <Navigate to="/trocar-senha" replace />
  }
  return <Outlet />
}

/** Rotas de um perfil. O backend é a autoridade; aqui só se evita mostrar telas inúteis. */
export function RequireRole({ roles }: { roles: Role[] }) {
  const { data: me } = useMe()
  if (!me) {
    return null
  }
  if (!roles.includes(me.role)) {
    return <Navigate to={landingPath(me.role)} replace />
  }
  return <Outlet />
}

export function HomeRedirect() {
  const { data: me } = useMe()
  return me ? <Navigate to={landingPath(me.role)} replace /> : null
}
