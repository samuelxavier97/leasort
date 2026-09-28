import type { ReactNode } from 'react'
import { PRODUCT_LOGO_URL, PRODUCT_NAME } from '@/lib/brand'
import { BrandIdentity } from './BrandIdentity'

/**
 * Moldura do login e da troca de senha (D-121): faixa na cor principal, a identidade do cliente em
 * destaque (D-117), o cartão do formulário e o Resortric no rodapé.
 */
export function AuthShell({ children }: { children: ReactNode }) {
  return (
    <div className="flex min-h-svh flex-col bg-muted/40">
      <div aria-hidden="true" className="h-1 bg-primary" />
      <main className="flex flex-1 items-center justify-center px-4 py-10">
        <div className="w-full max-w-sm space-y-6">
          <div className="flex flex-col items-center gap-2 text-center" data-testid="auth-identity">
            <BrandIdentity size="hero" />
            <p className="text-sm text-muted-foreground">Gestão de visitas</p>
          </div>
          {children}
        </div>
      </main>
      <footer className="flex justify-center px-4 py-4">
        <img src={PRODUCT_LOGO_URL} alt={PRODUCT_NAME} className="h-4 w-auto opacity-70" />
      </footer>
    </div>
  )
}
