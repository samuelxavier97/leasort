import { useState } from 'react'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'

/**
 * Exibe a senha temporária uma única vez (D-046). Ao fechar, o componente pai descarta o valor,
 * e ele não pode ser recuperado depois.
 */
export function TemporaryPasswordDialog({
  userName,
  password,
  onClose,
}: {
  userName: string
  password: string | null
  onClose: () => void
}) {
  const [copied, setCopied] = useState(false)

  const copy = async () => {
    await navigator.clipboard?.writeText(password ?? '')
    setCopied(true)
  }

  return (
    <Dialog
      open={password !== null}
      onOpenChange={(open) => {
        if (!open) {
          setCopied(false)
          onClose()
        }
      }}
    >
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Senha temporária</DialogTitle>
          <DialogDescription>
            Entregue esta senha a {userName}. Ela será exibida só agora, e a troca é obrigatória no primeiro acesso.
          </DialogDescription>
        </DialogHeader>
        <p className="rounded-md border bg-muted p-3 text-center font-mono text-lg tracking-wider" data-testid="temporary-password">
          {password}
        </p>
        <DialogFooter>
          <Button variant="outline" onClick={copy}>
            {copied ? 'Copiada' : 'Copiar'}
          </Button>
          <Button
            onClick={() => {
              setCopied(false)
              onClose()
            }}
          >
            Fechar
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
