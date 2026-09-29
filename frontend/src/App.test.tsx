import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { csrfRoute, fakeApi, problem, type Route } from './test/fakeApi'

let App: typeof import('./App').default
beforeEach(async () => {
  vi.resetModules()
  App = (await import('./App')).default
})

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <App />
    </MemoryRouter>,
  )
}

function visit(helloRoute: Route) {
  const fetch = fakeApi({ 'GET /csrf': csrfRoute(), 'GET /hello': helloRoute })
  renderAt('/')
  return fetch
}

describe('SPA walking skeleton', () => {
  it('bootstraps CSRF, calls /hello, and quietly shows the login page to a Visitor', async () => {
    const fetch = visit(() => problem(401, 'authentication_required'))

    expect(await screen.findByRole('heading', { name: 'Log in' })).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    const paths = fetch.mock.calls.map(([url]) => new URL(String(url)).pathname)
    expect(paths).toEqual(['/api/csrf', '/api/hello'])
  })

  it('renders the greeting as text, never as HTML', async () => {
    visit(() => new Response('<img src=x onerror=alert(1)>Hello', { headers: { 'Content-Type': 'text/plain' } }))

    const heading = await screen.findByRole('heading')
    expect(heading).toHaveTextContent('<img src=x onerror=alert(1)>Hello')
    expect(heading.querySelector('img')).toBeNull()
  })

  it('shows a generic error when /hello fails for another reason', async () => {
    visit(() => problem(500, 'internal_error'))

    expect(await screen.findByRole('alert')).toHaveTextContent('Something went wrong')
  })

  it('shows a generic error when the API cannot be reached', async () => {
    vi.spyOn(globalThis, 'fetch').mockRejectedValue(new TypeError('Failed to fetch'))
    renderAt('/')

    expect(await screen.findByRole('alert')).toHaveTextContent('Something went wrong')
  })

  it('shows a loading state until the API answers', () => {
    vi.spyOn(globalThis, 'fetch').mockReturnValue(new Promise(() => {}))
    renderAt('/')

    expect(screen.getByText('Loading…')).toBeInTheDocument()
  })

  it('sends unknown paths to the start page', async () => {
    visit(() => problem(401, 'authentication_required'))
    renderAt('/no-such-page')

    expect((await screen.findAllByRole('heading', { name: 'Log in' })).length).toBeGreaterThan(0)
  })
})
