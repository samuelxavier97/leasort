import { Navigate, type RouteObject } from 'react-router'
import { ChangePasswordPage } from '@/features/auth/ChangePasswordPage'
import { LoginPage } from '@/features/auth/LoginPage'
import { ProfilePage } from '@/features/profile/ProfilePage'
import { ProspectorsPage } from '@/features/prospectors/ProspectorsPage'
import { UsersPage } from '@/features/users/UsersPage'
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
            children: [{ path: '/dashboard', element: <ComingSoonPage title="Dashboard" /> }],
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
              { path: '/prospectores', element: <ProspectorsPage /> },
            ],
          },
          {
            element: <RequireRole roles={['PROSPECTOR']} />,
            children: [{ path: '/perfil', element: <ProfilePage /> }],
          },
        ],
      },
    ],
  },
  { path: '*', element: <Navigate to="/" replace /> },
]
