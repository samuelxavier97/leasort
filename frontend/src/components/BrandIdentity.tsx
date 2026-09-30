import { currentBrand, PRODUCT_NAME, productLogoUrl, type Brand } from '@/lib/brand'
import { cn } from '@/lib/utils'

const SIZES = {
  header: { logo: 'h-8 max-w-40', name: 'max-w-64 truncate font-semibold', product: 'h-6' },
  hero: { logo: 'h-20 max-w-72', name: 'text-2xl font-semibold text-balance', product: 'h-10' },
  // Ficha impressa: o logotipo cabe em 12 mm × 45 mm, para a ficha continuar em uma página A4 (D-025).
  sheet: { logo: 'h-12 max-w-48 print:h-[12mm] print:max-w-[45mm]', name: 'font-semibold', product: 'h-6 print:h-[6mm]' },
}

/**
 * Identidade do cliente (D-117): o logotipo, se houver; senão, `RESORT_NAME` em texto; senão, o
 * logotipo do Resortric (sem dourado se houver cor de cliente, D-126). O logotipo entra só por `<img>`,
 * nunca inline (D-119).
 */
export function BrandIdentity({ brand = currentBrand(), size = 'header' }: { brand?: Brand; size?: keyof typeof SIZES }) {
  const classes = SIZES[size]
  if (brand.logoUrl) {
    return (
      <img
        src={brand.logoUrl}
        alt={brand.resortName ?? 'Logotipo do Resort'}
        className={cn('w-auto object-contain', classes.logo)}
      />
    )
  }
  if (brand.resortName) {
    return <span className={classes.name}>{brand.resortName}</span>
  }
  return <img src={productLogoUrl(brand)} alt={PRODUCT_NAME} className={cn('w-auto', classes.product)} />
}
