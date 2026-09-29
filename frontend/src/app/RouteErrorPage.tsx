import { isRouteErrorResponse, useRouteError } from 'react-router'
import { ForgeBackdrop } from '../shared/scene/ForgeBackdrop.tsx'
import { Brand } from '../shared/ui/Brand.tsx'
import './RouteErrorPage.css'

/**
 * Replaces React Router's default error element, which shows the error message and stack
 * trace even in production builds. It replaces AppShell too, so it brings its own backdrop.
 */
export function RouteErrorPage() {
  const error = useRouteError()
  const notFound = isRouteErrorResponse(error) && error.status === 404

  return (
    <div className="error-page">
      <ForgeBackdrop mode="app" />
      <main className="error-card panel">
        <Brand />
        <p className="eyebrow">{notFound ? 'Error 404' : 'Unexpected error'}</p>
        <h1>{notFound ? 'Page not found' : 'Something went wrong'}</h1>
        <p className="muted">
          {notFound
            ? 'Check the address and try again.'
            : 'Please reload the page or try again later.'}
        </p>
        {/* A plain link: a full page load resets whatever state caused the error. */}
        <a className="btn btn--primary" href="/">
          Go to home
        </a>
      </main>
    </div>
  )
}
