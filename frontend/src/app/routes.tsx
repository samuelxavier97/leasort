import { Navigate, type RouteObject } from 'react-router'
import { ChangePasswordPage } from '@/features/auth/ChangePasswordPage'
import { LoginPage } from '@/features/auth/LoginPage'
import { InvitationPage } from '@/features/invitations/InvitationPage'
import { InvitationsPage } from '@/features/invitations/InvitationsPage'
import { ImportLeadsPage } from '@/features/leads/ImportLeadsPage'
import { LeadDetailPage } from '@/features/leads/LeadDetailPage'
import { LeadsPage } from '@/features/leads/LeadsPage'
import { ProfilePage } from '@/features/profile/ProfilePage'
import { ProspectorsPage } from '@/features/prospectors/ProspectorsPage'
import { UsersPage } from '@/features/users/UsersPage'
import { VisitDetailPage } from '@/features/visits/VisitDetailPage'
import { VisitsPage } from '@/features/visits/VisitsPage'
import { AppLayout } from './AppLayout'
import { ComingSoonPage } from './ComingSoonPage'
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
              { path: '/dashboard', element: <ComingSoonPage title="Dashboard" /> },
              { path: '/leads', element: <LeadsPage /> },
              { path: '/leads/:id', element: <LeadDetailPage /> },
              { path: '/visitas/:id', element: <VisitDetailPage /> },
              { path: '/convites', element: <InvitationsPage /> },
              { path: '/convites/:id', element: <InvitationPage /> },
            ],
          },
          {
            element: <RequireRole roles={['GATE']} />,
            children: [{ path: '/portaria', element: <ComingSoonPage title="Validar Convite" /> }],
          },
          {
            element: <RequireRole roles={['HOST']} />,
            children: [{ path: '/chegadas', element: <ComingSoonPage title="Chegadas de hoje" /> }],
          },
          {
            element: <RequireRole roles={['ADMIN']} />,
            children: [
              { path: '/usuarios', element: <UsersPage /> },
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
