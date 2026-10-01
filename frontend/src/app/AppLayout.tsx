import { useMutation, useQueryClient } from '@tanstack/react-query'
import { ChevronDown, Menu } from 'lucide-react'
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router'
import { BrandIdentity } from '@/components/BrandIdentity'
import { BrandStripe } from '@/components/BrandStripe'
import { ProductFooter } from '@/components/ProductFooter'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { logout, meQueryKey } from '@/features/auth/api'
import { useMe } from '@/features/auth/useMe'
import { cn } from '@/lib/utils'
import { ADMIN_GROUP, NAV_ITEMS, ROLE_LABELS, screenTitle } from './navigation'
import { useDocumentTitle } from './useDocumentTitle'

const NAV_ITEM = 'rounded-t-md border-b-2 border-transparent px-2.5 py-2 text-sm hover:bg-muted'
const NAV_ITEM_ACTIVE = 'border-primary-edge bg-soft-strong font-semibold text-foreground'

export function AppLayout() {
  const { data: me } = useMe()
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const location = useLocation()
  useDocumentTitle(me ? screenTitle(location.pathname, me.role) : null)
  const logoutMutation = useMutation({
    mutationFn: logout,
    onSettled: () => {
      queryClient.clear()
      queryClient.setQueryData(meQueryKey, null)
      navigate('/login', { replace: true })
    },
  })

  if (!me) {
    return null
  }
  const items = NAV_ITEMS[me.role]
  const grouped = items.filter((item) => ADMIN_GROUP.paths.includes(item.to))
  const groupActive = grouped.some((item) => location.pathname.startsWith(item.to))

  return (
    <div className="flex min-h-svh flex-col bg-page print:block print:min-h-0 print:bg-white">
      {/* Faixa na cor principal do cliente (D-117, D-126); o cabeçalho continua branco, para o logotipo. */}
      <BrandStripe className="print:hidden" />
      <header className="border-b bg-background print:hidden">
        <div className="mx-auto flex max-w-6xl items-center gap-x-4 gap-y-2 px-4 py-3 md:flex-wrap">
          <Link to="/" className="flex min-w-0 shrink items-center">
            <BrandIdentity />
          </Link>
          {/* Computador: o menu e o usuário no cabeçalho. Celular: tudo no botão "Menu" (D-122). */}
          <nav aria-label="Menu principal" className="hidden flex-1 flex-wrap gap-1 md:flex">
            {items
              .filter((item) => !grouped.includes(item))
              .map((item) => (
                <NavLink
                  key={item.to}
                  to={item.to}
                  // Item ativo: fundo, negrito e a linha na cor de borda da D-118, para não depender só da
                  // cor principal (uma cor clara some no branco).
                  className={({ isActive }) => cn(NAV_ITEM, isActive && NAV_ITEM_ACTIVE)}
                >
                  {item.label}
                </NavLink>
              ))}
            {grouped.length > 0 && (
              <DropdownMenu>
                <DropdownMenuTrigger className={cn(NAV_ITEM, 'inline-flex items-center gap-1', groupActive && NAV_ITEM_ACTIVE)}>
                  {ADMIN_GROUP.label}
                  <ChevronDown className="size-4" />
                </DropdownMenuTrigger>
                <DropdownMenuContent align="start">
                  {grouped.map((item) => (
                    <DropdownMenuItem
                      key={item.to}
                      asChild
                      className={cn(
                        'min-h-9 border-l-2 border-transparent',
                        location.pathname.startsWith(item.to) && 'border-primary-edge bg-soft-strong font-semibold',
                      )}
                    >
                      <NavLink to={item.to}>{item.label}</NavLink>
                    </DropdownMenuItem>
                  ))}
                </DropdownMenuContent>
              </DropdownMenu>
            )}
          </nav>
          <div className="hidden md:block">
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button variant="ghost" size="sm" title={me.name}>
                  <span className="max-w-52 truncate" data-testid="header-user-name">{me.name}</span>
                  <ChevronDown />
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end">
                <UserLabel name={me.name} role={ROLE_LABELS[me.role]} />
                <DropdownMenuSeparator />
                <DropdownMenuItem asChild>
                  <Link to="/trocar-senha">Trocar senha</Link>
                </DropdownMenuItem>
                <DropdownMenuItem onSelect={() => logoutMutation.mutate()}>Sair</DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          </div>
          <div className="ml-auto shrink-0 md:hidden">
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button variant="outline" className="h-10">
                  <Menu />
                  Menu
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end" className="w-64">
                <UserLabel name={me.name} role={ROLE_LABELS[me.role]} />
                <DropdownMenuSeparator />
                {items.map((item) => (
                  // O asChild do Radix junta as classes como texto: o item ativo sai da rota atual.
                  <DropdownMenuItem
                    key={item.to}
                    asChild
                    className={cn(
                      'min-h-10 border-l-2 border-transparent',
                      location.pathname === item.to && 'border-primary-edge bg-soft-strong font-semibold',
                    )}
                  >
                    <NavLink to={item.to}>{item.label}</NavLink>
                  </DropdownMenuItem>
                ))}
                <DropdownMenuSeparator />
                <DropdownMenuItem asChild className="min-h-10">
                  <Link to="/trocar-senha">Trocar senha</Link>
                </DropdownMenuItem>
                <DropdownMenuItem className="min-h-10" onSelect={() => logoutMutation.mutate()}>
                  Sair
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          </div>
        </div>
      </header>
      <main className="mx-auto w-full max-w-6xl flex-1 px-4 py-6 print:max-w-none print:p-0">
        <Outlet />
      </main>
      <ProductFooter className="print:hidden" />
    </div>
  )
}

function UserLabel({ name, role }: { name: string; role: string }) {
  return (
    <DropdownMenuLabel>
      <span className="block">{name}</span>
      <span className="block text-xs font-normal text-muted-foreground">{role}</span>
    </DropdownMenuLabel>
  )
}
