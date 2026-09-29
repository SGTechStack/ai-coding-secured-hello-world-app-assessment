import { Component, type ReactNode } from 'react'

type Props = { children: ReactNode }
type State = { failed: boolean }

/**
 * Catches render errors anywhere below it and shows a generic message instead of a blank page.
 * Error details are never shown to the user.
 */
export class ErrorBoundary extends Component<Props, State> {
  state: State = { failed: false }

  static getDerivedStateFromError(): State {
    return { failed: true }
  }

  render() {
    if (this.state.failed) {
      return (
        <main className="app">
          <p role="alert">Something went wrong. Please reload the page and try again.</p>
        </main>
      )
    }
    return this.props.children
  }
}
