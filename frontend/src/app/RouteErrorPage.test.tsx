import { render, screen } from '@testing-library/react'
import { RouterProvider, createMemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { RouteErrorPage } from './RouteErrorPage.tsx'

function Broken(): never {
  throw new Error('internal detail that must not leak')
}

describe('RouteErrorPage', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('shows a generic message without the error details when a route throws', () => {
    // React reports the caught render error on the console; keep test output clean.
    vi.spyOn(console, 'error').mockImplementation(() => {})
    const router = createMemoryRouter([
      { path: '/', element: <Broken />, errorElement: <RouteErrorPage /> },
    ])

    render(<RouterProvider router={router} />)

    expect(screen.getByRole('heading', { name: 'Something went wrong' })).toBeInTheDocument()
    expect(screen.queryByText(/internal detail/)).not.toBeInTheDocument()
  })
})
