import type { ReactNode } from 'react'
import { BrandIdentity } from './BrandIdentity'
import { BrandStripe } from './BrandStripe'
import { ProductFooter } from './ProductFooter'

/**
 * Moldura do login e da troca de senha (D-121): faixa na cor principal, a identidade do cliente em
 * destaque (D-117) com a linha curta de detalhe (D-126), o cartão do formulário e o rodapé com o Resortric.
 */
export function AuthShell({ children }: { children: ReactNode }) {
  return (
    <div className="flex min-h-svh flex-col bg-page">
      <BrandStripe />
      <main className="flex flex-1 items-center justify-center px-4 py-10">
        <div className="w-full max-w-sm space-y-6">
          <div className="flex flex-col items-center gap-2 text-center" data-testid="auth-identity">
            <BrandIdentity size="hero" />
            <p className="text-sm text-muted-foreground">Gestão de visitas</p>
            {/* Linha curta: dourada na identidade do Resortric; com tema de cliente, na cor principal (D-126). */}
            <div aria-hidden="true" data-testid="auth-accent" className="mt-1 h-0.5 w-10 rounded-full bg-brand-accent" />
          </div>
          {children}
        </div>
      </main>
      <ProductFooter />
    </div>
  )
}
