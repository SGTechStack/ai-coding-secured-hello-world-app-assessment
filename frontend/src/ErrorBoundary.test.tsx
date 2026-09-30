import { render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ErrorBoundary } from './ErrorBoundary'
import { fakeApi } from './test/fakeApi'

function Broken(): never {
  throw new Error('SELECT password_hash FROM users')
}

// A caught render error is reported to the API, so every test here stands in for the network. Each
// test awaits the report it causes, so no request is left in flight when the fake is torn down.
let api: ReturnType<typeof fakeApi>
beforeEach(() => {
  api = fakeApi({ 'POST /client-events': () => new Response(null, { status: 204 }) })
})

const reportSent = () => vi.waitUntil(() => api.mock.calls.find(([, init]) => init?.method === 'POST'))

describe('ErrorBoundary', () => {
  it('renders its children when nothing fails', () => {
    render(
      <ErrorBoundary>
        <p>content</p>
      </ErrorBoundary>,
    )

    expect(screen.getByText('content')).toBeInTheDocument()
    expect(api).not.toHaveBeenCalled()
  })

  it('replaces a crashed tree with a generic message that reveals no error details', async () => {
    vi.spyOn(console, 'error').mockImplementation(() => {})

    render(
      <ErrorBoundary>
        <Broken />
      </ErrorBoundary>,
    )
    await reportSent()

    expect(screen.getByRole('alert')).toHaveTextContent('Something went wrong')
    expect(document.body).not.toHaveTextContent('SELECT')
  })

  it('tells the operator a render error happened, without the error itself', async () => {
    vi.spyOn(console, 'error').mockImplementation(() => {})

    render(
      <ErrorBoundary>
        <Broken />
      </ErrorBoundary>,
    )
    const report = await reportSent()

    expect(JSON.parse(String(report[1]?.body))).toEqual({ kind: 'RENDER_ERROR', path: '/' })
    expect(String(report[1]?.body)).not.toContain('SELECT')
  })
})
