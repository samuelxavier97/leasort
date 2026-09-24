import { Button } from '@/components/ui/button'

export function Pagination({
  page,
  totalPages,
  onChange,
}: {
  page: number
  totalPages: number
  onChange: (page: number) => void
}) {
  if (totalPages <= 1) {
    return null
  }
  return (
    <div className="flex items-center justify-end gap-2 text-sm">
      <Button variant="outline" size="sm" disabled={page === 0} onClick={() => onChange(page - 1)}>
        Anterior
      </Button>
      <span>
        Página {page + 1} de {totalPages}
      </span>
      <Button variant="outline" size="sm" disabled={page + 1 >= totalPages} onClick={() => onChange(page + 1)}>
        Próxima
      </Button>
    </div>
  )
}
