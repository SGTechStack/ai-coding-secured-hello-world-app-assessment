import { http, HttpResponse, type RequestHandler } from 'msw'
import { apiUrl, CSRF_HEADER } from '@/lib/api/client'

/**
 * Default MSW handlers shared by every Vitest test. Tests add per-case handlers with `server.use(...)`.
 * API URLs are built from `import.meta.env.VITE_API_ORIGIN` so fixtures match what the SPA calls.
 */
export const handlers: RequestHandler[] = [
  // The CSRF bootstrap every unsafe request needs first (ADR-036).
  http.get(apiUrl('/api/csrf'), () => HttpResponse.json({ headerName: CSRF_HEADER, token: 'test-csrf-token' })),
]
