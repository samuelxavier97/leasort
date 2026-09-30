import { useMutation, useQueryClient } from '@tanstack/react-query'
import { ChevronDown, Menu } from 'lucide-react'
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router'
import { BrandIdentity } from '@/components/BrandIdentity'
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
import { NAV_ITEMS, ROLE_LABELS, screenTitle } from './navigation'
import { useDocumentTitle } from './useDocumentTitle'

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

  return (
    <div className="flex min-h-svh flex-col bg-muted/40 print:block print:min-h-0 print:bg-white">
      {/* Faixa na cor principal do cliente (D-117); o cabeçalho continua branco, para o logotipo. */}
      <div aria-hidden="true" className="h-1 bg-primary print:hidden" />
      <header className="border-b bg-background print:hidden">
        <div className="mx-auto flex max-w-6xl items-center gap-x-6 gap-y-2 px-4 py-3 md:flex-wrap">
          <Link to="/" className="flex min-w-0 shrink items-center">
            <BrandIdentity />
          </Link>
          {/* Computador: o menu e o usuário no cabeçalho. Celular: tudo no botão "Menu" (D-122). */}
          <nav aria-label="Menu principal" className="hidden flex-1 flex-wrap gap-1 md:flex">
            {NAV_ITEMS[me.role].map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                // Item ativo: fundo, negrito e a linha na cor de borda da D-118, para não depender só da
                // cor principal (uma cor clara some no branco).
                className={({ isActive }) =>
                  cn(
                    'rounded-t-md border-b-2 border-transparent px-3 py-2 text-sm hover:bg-muted',
                    isActive && 'border-primary-edge bg-muted font-semibold text-foreground',
                  )
                }
              >
                {item.label}
              </NavLink>
            ))}
          </nav>
          <div className="hidden md:block">
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button variant="ghost" size="sm">
                  {me.name}
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
                {NAV_ITEMS[me.role].map((item) => (
                  // O asChild do Radix junta as classes como texto: o item ativo sai da rota atual.
                  <DropdownMenuItem
                    key={item.to}
                    asChild
                    className={cn(
                      'min-h-10 border-l-2 border-transparent',
                      location.pathname === item.to && 'border-primary-edge bg-muted font-semibold',
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
