import { cn } from '@/lib/utils'
import { DEMO_LABEL, isDemoInstance, PRODUCT_LOGO_URL, PRODUCT_NAME } from '@/lib/brand'

/**
 * Rodapé de todas as telas: o Resortric (D-117) e, numa instalação de demonstração, a etiqueta
 * "Ambiente de demonstração" ao lado (D-125). A imagem do convite e a ficha impressa não usam este
 * rodapé.
 */
export function ProductFooter({ className }: { className?: string }) {
  return (
    <footer className={cn('flex items-center justify-center gap-2 px-4 py-4', className)}>
      <img src={PRODUCT_LOGO_URL} alt={PRODUCT_NAME} className="h-4 w-auto opacity-70" />
      {isDemoInstance() && <span className="text-xs text-muted-foreground">{DEMO_LABEL}</span>}
    </footer>
  )
}
