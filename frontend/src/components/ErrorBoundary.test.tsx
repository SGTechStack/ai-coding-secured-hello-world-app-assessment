import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { ErrorBoundary } from './ErrorBoundary'

function Broken(): never {
  throw new Error('SQLException at internal-db-host:5432')
}

describe('ErrorBoundary', () => {
  it('shows a generic message without the error details', () => {
    // React reports the caught error to console.error; keep test output clean.
    vi.spyOn(console, 'error').mockImplementation(() => {})

    render(
      <ErrorBoundary>
        <Broken />
      </ErrorBoundary>,
    )

    expect(screen.getByRole('alert')).toHaveTextContent('Something went wrong')
    expect(document.body).not.toHaveTextContent('internal-db-host')
  })
})
