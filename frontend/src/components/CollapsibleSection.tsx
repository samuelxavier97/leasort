import { ChevronDown, ChevronRight } from 'lucide-react'
import { useId, type ReactNode } from 'react'

/** Seção opcional de formulário que começa recolhida (SPEC §16.3). O conteúdo fica montado, só oculto. */
export function CollapsibleSection({
  title,
  open,
  onToggle,
  children,
}: {
  title: string
  open: boolean
  onToggle: () => void
  children: ReactNode
}) {
  const contentId = useId()
  const Icon = open ? ChevronDown : ChevronRight
  return (
    <div className="rounded-md border">
      <button
        type="button"
        className="flex w-full items-center gap-2 px-3 py-2 text-left text-sm font-medium"
        aria-expanded={open}
        aria-controls={contentId}
        onClick={onToggle}
      >
        <Icon className="size-4" aria-hidden />
        {title}
      </button>
      <div id={contentId} hidden={!open} className="border-t p-3">
        {children}
      </div>
    </div>
  )
}
