import { QueryClient } from '@tanstack/react-query'
import type { RouteObject } from 'react-router'
import { Layout } from '@/components/Layout'
import { shouldRetry } from '@/lib/api/client'
import { ActivatePage } from '@/pages/ActivatePage'
import { ChangePasswordPage } from '@/pages/ChangePasswordPage'
import { ForgotPasswordPage } from '@/pages/ForgotPasswordPage'
import { HelloPage } from '@/pages/HelloPage'
import { HomePage } from '@/pages/HomePage'
import { RegisterPage } from '@/pages/RegisterPage'
import { ResetPasswordPage } from '@/pages/ResetPasswordPage'
import { SignInPage } from '@/pages/SignInPage'

/** Public: sign-in, register, activate, forgot and reset. Signed in: hello and change password. The entry route decides between them from the self-read. */
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
    ],
  },
]

/** The app's query client. Only a request that got no status is retried (REJ-052). */
export function createQueryClient() {
  return new QueryClient({ defaultOptions: { queries: { retry: shouldRetry } } })
}
