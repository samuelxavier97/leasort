import { Link } from 'react-router'
import { ROLE_LABELS } from '@/app/navigation'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { useMe } from '@/features/auth/useMe'

/** Dados do próprio usuário, somente leitura, e acesso à troca de senha (D-045). */
export function ProfilePage() {
  const { data: me } = useMe()
  if (!me) {
    return null
  }
  return (
    <section className="max-w-lg space-y-4">
      <h1 className="text-2xl font-semibold">Perfil</h1>
      <Card>
        <CardHeader>
          <CardTitle>{me.name}</CardTitle>
        </CardHeader>
        <CardContent className="space-y-3">
          <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-2 text-sm">
            <dt className="text-muted-foreground">E-mail</dt>
            <dd>{me.email}</dd>
            <dt className="text-muted-foreground">Perfil</dt>
            <dd>{ROLE_LABELS[me.role]}</dd>
          </dl>
          <Button asChild variant="outline">
            <Link to="/trocar-senha">Trocar senha</Link>
          </Button>
        </CardContent>
      </Card>
    </section>
  )
}
