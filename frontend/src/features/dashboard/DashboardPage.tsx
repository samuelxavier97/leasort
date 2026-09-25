import { useMe } from '@/features/auth/useMe'
import { AdminDashboard } from './AdminDashboard'
import { ProspectorDashboard } from './ProspectorDashboard'

/** Tela inicial do ADMIN e do PROSPECTOR (§16.1, §16.7). */
export function DashboardPage() {
  const { data: me } = useMe()
  return me?.role === 'ADMIN' ? <AdminDashboard /> : <ProspectorDashboard />
}
