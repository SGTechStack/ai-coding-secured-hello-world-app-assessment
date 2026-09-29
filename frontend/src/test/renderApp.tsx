import { render } from '@testing-library/react'
import { RouterProvider, createMemoryRouter } from 'react-router'
import { wireUnauthorizedRedirect } from '../app/router.ts'
import { routes } from '../app/routes.tsx'
import { type ApiRoutes, jsonResponse, textResponse } from './fakeApi.ts'

/** Renders the real route table at `path`, wired like main.tsx, and returns its router. */
export function renderApp(path: string) {
  const router = createMemoryRouter(routes, { initialEntries: [path] })
  wireUnauthorizedRedirect(router)
  render(<RouterProvider router={router} />)
  return router
}

/** Loader answers for a visitor without a session. */
export const ANONYMOUS: ApiRoutes = {
  'GET /auth/me': () => jsonResponse({}, 401),
  'GET /hello': () => jsonResponse({}, 401),
}

/** Loader answers for a signed-in account. */
export function signedIn(username: string, role: 'USER' | 'ADMIN'): ApiRoutes {
  return {
    'GET /auth/me': () => jsonResponse({ username, role }),
    'GET /hello': () => textResponse(`Hello, ${username}`),
  }
}
