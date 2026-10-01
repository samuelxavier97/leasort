import { cn } from '@/lib/utils'
import { currentBrand, DEMO_LABEL, isDemoInstance, PRODUCT_NAME, productLogoUrl } from '@/lib/brand'

/**
 * Rodapé de todas as telas: o Resortric (D-117) e, numa instalação de demonstração, a etiqueta
 * "Ambiente de demonstração" ao lado (D-125). Com tema de cliente, o logotipo sem dourado (D-126). A imagem do convite e a ficha impressa não usam este
 * rodapé.
 */
export function ProductFooter({ className }: { className?: string }) {
  return (
    <footer className={cn('flex items-center justify-center gap-2 px-4 py-4', className)}>
      <img src={productLogoUrl(currentBrand())} alt={PRODUCT_NAME} className="h-4 w-auto opacity-70" />
      {isDemoInstance() && <span className="text-xs text-muted-foreground">{DEMO_LABEL}</span>}
    </footer>
  )
}
