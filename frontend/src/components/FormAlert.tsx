export function FormAlert({ message }: { message?: string | null }) {
  if (!message) {
    return null
  }
  return (
    <p role="alert" className="rounded-md border border-destructive/30 bg-destructive/5 p-3 text-sm text-destructive">
      {message}
    </p>
  )
}
