import { QueryClient } from '@tanstack/react-query'
import type { RouteObject } from 'react-router'
import { AdminGate } from '@/components/AdminGate'
import { Layout } from '@/components/Layout'
import { shouldRetry } from '@/lib/api/client'
import { FACTOR_DISABLED_ROUTE } from '@/lib/auth/session'
import { ActivatePage } from '@/pages/ActivatePage'
import { AdminInvitePage } from '@/pages/AdminInvitePage'
import { AdminUserDetailPage } from '@/pages/AdminUserDetailPage'
import { AdminUsersPage } from '@/pages/AdminUsersPage'
import { ChangePasswordPage } from '@/pages/ChangePasswordPage'
import { FactorChallengePage } from '@/pages/FactorChallengePage'
import { FactorDisabledPage } from '@/pages/FactorDisabledPage'
import { ForgotPasswordPage } from '@/pages/ForgotPasswordPage'
import { HelloPage } from '@/pages/HelloPage'
import { HomePage } from '@/pages/HomePage'
import { MfaSettingsPage } from '@/pages/MfaSettingsPage'
import { RegisterPage } from '@/pages/RegisterPage'
import { ResetPasswordPage } from '@/pages/ResetPasswordPage'
import { SignInPage } from '@/pages/SignInPage'

/**
 * The admin surface: the user list and one user, behind the admin gate. The gate is UX only; the pages render nothing
 * the server did not return (T-FE-002).
 */
export const adminRoutes: RouteObject[] = [
  { path: '/admin/users', element: <AdminUsersPage /> },
  { path: '/admin/users/:id', element: <AdminUserDetailPage /> },
  { path: '/admin/users/invite', element: <AdminInvitePage /> },
]

/**
 * Public: sign-in, register, activate, forgot and reset. Signed in: hello and change password. Administrators only:
 * `/settings/mfa`, the challenge at `/verify`, the terminal factor state at `/factor-disabled`, and the admin surface.
 * The entry route decides between them from the self-read, in gate order.
 */
export const routes: RouteObject[] = [
  {
    element: <Layout />,
    children: [
      { path: '/', element: <HomePage /> },
      { path: '/sign-in', element: <SignInPage /> },
      { path: '/register', element: <RegisterPage /> },
      { path: '/activate', element: <ActivatePage /> },
      { path: '/forgot-password', element: <ForgotPasswordPage /> },
      { path: '/reset', element: <ResetPasswordPage /> },
      { path: '/hello', element: <HelloPage /> },
      { path: '/change-password', element: <ChangePasswordPage /> },
      { path: '/settings/mfa', element: <MfaSettingsPage /> },
      { path: '/verify', element: <FactorChallengePage /> },
      { path: FACTOR_DISABLED_ROUTE, element: <FactorDisabledPage /> },
      { element: <AdminGate />, children: adminRoutes },
    ],
  },
]

/** The app's query client. Only a request that got no status is retried (REJ-052). */
export function createQueryClient() {
  return new QueryClient({ defaultOptions: { queries: { retry: shouldRetry } } })
}
