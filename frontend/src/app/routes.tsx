import { Outlet, type RouteObject } from 'react-router'
import { AdminUsersPage } from '../features/admin/AdminUsersPage.tsx'
import { adminUsersLoader } from '../features/admin/loaders.ts'
import { LandingPage } from '../features/auth/LandingPage.tsx'
import { LoginPage } from '../features/auth/LoginPage.tsx'
import { landingLoader, loginLoader, protectedLoader } from '../features/auth/loaders.ts'
import { ForgotPasswordPage } from '../features/password-reset/ForgotPasswordPage.tsx'
import { ResetPasswordPage } from '../features/password-reset/ResetPasswordPage.tsx'
import { RegisterPage } from '../features/registration/RegisterPage.tsx'
import { AppShell } from './AppShell.tsx'
import { AuthLayout } from './AuthLayout.tsx'
import { RouteErrorPage } from './RouteErrorPage.tsx'

// Feature routes remain under the root error boundary so loader failures never expose details.
export const routes: RouteObject[] = [
  {
    element: <AppShell />,
    errorElement: <RouteErrorPage />,
    children: [
      {
        element: <AuthLayout />,
        children: [
          {
            path: '/login',
            loader: loginLoader,
            element: <LoginPage />,
          },
          {
            path: '/register',
            loader: loginLoader,
            element: <RegisterPage />,
          },
          {
            path: '/forgot-password',
            loader: loginLoader,
            element: <ForgotPasswordPage />,
          },
          {
            path: '/reset-password',
            loader: loginLoader,
            element: <ResetPasswordPage />,
          },
        ],
      },
      {
        id: 'protected',
        loader: protectedLoader,
        element: <Outlet />,
        children: [
          {
            index: true,
            loader: landingLoader,
            element: <LandingPage />,
          },
          {
            path: '/admin/users',
            loader: adminUsersLoader,
            element: <AdminUsersPage />,
          },
        ],
      },
    ],
  },
]
