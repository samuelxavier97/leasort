import { useEffect } from 'react'
import { currentBrand, documentTitle } from '@/lib/brand'

/** Título da aba: "Tela · RESORT_NAME", ou "Tela · Resortric" sem o nome (D-117). */
export function useDocumentTitle(screen: string | null) {
  useEffect(() => {
    document.title = documentTitle(screen, currentBrand())
  }, [screen])
}
