import { currentBrand, PRODUCT_LOGO_URL, PRODUCT_NAME, type Brand } from '@/lib/brand'

/**
 * Identidade do cliente no cabeçalho (D-117): o logotipo, se houver; senão, `RESORT_NAME` em texto;
 * senão, o logotipo do Resortric. O logotipo entra só por `<img>`, nunca inline (D-119).
 */
export function BrandIdentity({ brand = currentBrand() }: { brand?: Brand }) {
  if (brand.logoUrl) {
    return (
      <img src={brand.logoUrl} alt={brand.resortName ?? 'Logotipo do Resort'} className="h-8 w-auto max-w-40 object-contain" />
    )
  }
  if (brand.resortName) {
    return <span className="max-w-64 truncate font-semibold">{brand.resortName}</span>
  }
  return <img src={PRODUCT_LOGO_URL} alt={PRODUCT_NAME} className="h-6 w-auto" />
}
