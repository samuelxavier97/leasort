import { MutationCache, QueryCache, QueryClient } from '@tanstack/react-query'
import { meQueryKey } from '@/features/auth/api'
import { ApiError } from '@/lib/api'

/**
 * Sessão expirada ou encerrada pelo ADMIN (D-047) volta ao login; troca de senha exigida recarrega
 * o usuário para que o guard leve à tela de troca.
 */
export function createQueryClient(): QueryClient {
  const client: QueryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, refetchOnWindowFocus: false } },
    queryCache: new QueryCache({ onError: (error) => handleSessionError(client, error) }),
    mutationCache: new MutationCache({ onError: (error) => handleSessionError(client, error) }),
  })
  return client
}

function handleSessionError(client: QueryClient, error: unknown) {
  if (!(error instanceof ApiError)) {
    return
  }
  if (error.code === 'UNAUTHENTICATED') {
    client.setQueryData(meQueryKey, null)
  } else if (error.code === 'PASSWORD_CHANGE_REQUIRED') {
    client.invalidateQueries({ queryKey: meQueryKey })
  }
}
