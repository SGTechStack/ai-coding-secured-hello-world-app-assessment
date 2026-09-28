import { CSPProvider } from '@base-ui/react/csp-provider'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useState } from 'react'
import { createBrowserRouter, RouterProvider } from 'react-router'
import { HomePage } from '@/pages/HomePage'

const routes = [{ path: '/', element: <HomePage /> }]

export function App() {
  const [queryClient] = useState(() => new QueryClient())
  const [router] = useState(() => createBrowserRouter(routes))

  return (
    // Base UI must not inject <style> elements: the production style-src has no 'unsafe-inline' (R-HDR-001).
    <CSPProvider disableStyleElements>
      <QueryClientProvider client={queryClient}>
        <RouterProvider router={router} />
      </QueryClientProvider>
    </CSPProvider>
  )
}
