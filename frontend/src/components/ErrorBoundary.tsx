import { Component, type ErrorInfo, type ReactNode } from 'react'

interface State {
  failed: boolean
}

/**
 * Last-resort fallback for rendering errors. Shows a generic message only: error details can
 * reveal internals, so they are logged in development and never rendered.
 */
export class ErrorBoundary extends Component<{ children: ReactNode }, State> {
  state: State = { failed: false }

  static getDerivedStateFromError(): State {
    return { failed: true }
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    if (import.meta.env.DEV) {
      console.error('Unhandled rendering error', error, info.componentStack)
    }
  }

  render() {
    if (!this.state.failed) {
      return this.props.children
    }
    return (
      <main>
        <section className="card" role="alert">
          <h1>Something went wrong</h1>
          <p className="muted">Please reload the page. If the problem continues, try again later.</p>
          <button type="button" onClick={() => window.location.reload()}>
            Reload
          </button>
        </section>
      </main>
    )
  }
}
