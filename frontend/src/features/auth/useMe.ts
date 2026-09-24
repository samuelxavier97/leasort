import { useQuery } from '@tanstack/react-query'
import { fetchMe, meQueryKey } from './api'

export function useMe() {
  return useQuery({ queryKey: meQueryKey, queryFn: fetchMe, staleTime: Infinity, retry: false })
}
