import type { RequestHandler } from 'msw'

/**
 * Default MSW handlers shared by every Vitest test. Tests add per-case handlers with `server.use(...)`.
 * API URLs are built from `import.meta.env.VITE_API_ORIGIN` so fixtures match what the SPA calls.
 */
export const handlers: RequestHandler[] = []
