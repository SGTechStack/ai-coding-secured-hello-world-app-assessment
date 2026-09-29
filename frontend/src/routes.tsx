import { QueryClient } from '@tanstack/react-query'
import type { RouteObject } from 'react-router'
import { Layout } from '@/components/Layout'
import { shouldRetry } from '@/lib/api/client'
import { HelloPage } from '@/pages/HelloPage'
import { HomePage } from '@/pages/HomePage'
import { SignInPage } from '@/pages/SignInPage'

/** Public: sign-in. Signed in: hello. The entry route decides between them from the self-read. */
export const routes: RouteObject[] = [
  {
    element: <Layout />,
    children: [
      { path: '/', element: <HomePage /> },
      { path: '/sign-in', element: <SignInPage /> },
      { path: '/hello', element: <HelloPage /> },
    ],
  },
]

/** The app's query client. Only a request that got no status is retried (REJ-052). */
export function createQueryClient() {
  return new QueryClient({ defaultOptions: { queries: { retry: shouldRetry } } })
}
