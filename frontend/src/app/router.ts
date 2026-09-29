import { clearCsrfToken, setUnauthorizedHandler } from '../shared/api/http.ts'

interface NavigatingRouter {
  navigate(to: string, options: { replace: boolean }): unknown
}

/** Connects transport-level unexpected 401s to the active application router. */
export function wireUnauthorizedRedirect(router: NavigatingRouter): () => void {
  setUnauthorizedHandler(() => {
    clearCsrfToken()
    void router.navigate('/login', { replace: true })
  })
  return () => setUnauthorizedHandler(undefined)
}
