import { Navigate, type RouteObject } from 'react-router'
import { ChangePasswordPage } from '@/features/auth/ChangePasswordPage'
import { LoginPage } from '@/features/auth/LoginPage'
import { InvitationPage } from '@/features/invitations/InvitationPage'
import { InvitationsPage } from '@/features/invitations/InvitationsPage'
import { GatePage } from '@/features/access/GatePage'
import { RecentAccessPage } from '@/features/access/RecentAccessPage'
import { DashboardPage } from '@/features/dashboard/DashboardPage'
import { ArrivalsPage } from '@/features/arrivals/ArrivalsPage'
import { VisitSheetPage } from '@/features/arrivals/VisitSheetPage'
import { AuditPage } from '@/features/audit/AuditPage'
import { ExportsPage } from '@/features/exports/ExportsPage'
import { ImportLeadsPage } from '@/features/leads/ImportLeadsPage'
import { LeadDetailPage } from '@/features/leads/LeadDetailPage'
import { LeadsPage } from '@/features/leads/LeadsPage'
import { ProfilePage } from '@/features/profile/ProfilePage'
import { ProspectorsPage } from '@/features/prospectors/ProspectorsPage'
import { UsersPage } from '@/features/users/UsersPage'
import { VisitDetailPage } from '@/features/visits/VisitDetailPage'
import { VisitsPage } from '@/features/visits/VisitsPage'
import { AppLayout } from './AppLayout'
import { HomeRedirect, RequireAuth, RequireRole } from './guards'

export const routes: RouteObject[] = [
  { path: '/login', element: <LoginPage /> },
  {
    element: <RequireAuth />,
    children: [
      { path: '/trocar-senha', element: <ChangePasswordPage /> },
      {
        element: <AppLayout />,
        children: [
          { index: true, element: <HomeRedirect /> },
          {
            element: <RequireRole roles={['ADMIN', 'PROSPECTOR']} />,
            children: [
              { path: '/dashboard', element: <DashboardPage /> },
              { path: '/leads', element: <LeadsPage /> },
              { path: '/leads/:id', element: <LeadDetailPage /> },
              { path: '/visitas/:id', element: <VisitDetailPage /> },
              { path: '/convites', element: <InvitationsPage /> },
              { path: '/convites/:id', element: <InvitationPage /> },
            ],
          },
          {
            element: <RequireRole roles={['GATE']} />,
            children: [{ path: '/portaria', element: <GatePage /> }],
          },
          {
            // Rota própria, fora da proteção do /portaria: o ADMIN consulta acessos, mas não valida convites (§4.5).
            element: <RequireRole roles={['GATE', 'ADMIN']} />,
            children: [{ path: '/acessos', element: <RecentAccessPage /> }],
          },
          {
            element: <RequireRole roles={['ADMIN', 'PROSPECTOR', 'HOST']} />,
            children: [
              { path: '/chegadas', element: <ArrivalsPage /> },
              { path: '/chegadas/:visitId/ficha', element: <VisitSheetPage /> },
            ],
          },
          {
            element: <RequireRole roles={['ADMIN']} />,
            children: [
              { path: '/usuarios', element: <UsersPage /> },
              { path: '/exportacoes', element: <ExportsPage /> },
              { path: '/auditoria', element: <AuditPage /> },
              { path: '/leads/importar', element: <ImportLeadsPage /> },
              { path: '/prospectores', element: <ProspectorsPage /> },
              { path: '/visitas', element: <VisitsPage key="all" view="all" /> },
            ],
          },
          {
            element: <RequireRole roles={['PROSPECTOR']} />,
            children: [
              { path: '/agenda', element: <VisitsPage key="agenda" view="agenda" /> },
              { path: '/historico', element: <VisitsPage key="history" view="history" /> },
              { path: '/perfil', element: <ProfilePage /> },
            ],
          },
        ],
      },
    ],
  },
  { path: '*', element: <Navigate to="/" replace /> },
]
