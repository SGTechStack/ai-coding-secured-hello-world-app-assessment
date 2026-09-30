import { fireEvent, render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { csrfRoute, fakeApi, problem, type Route } from '../test/fakeApi'

let App: typeof import('../App').default
beforeEach(async () => {
  vi.resetModules()
  App = (await import('../App')).default
})

function renderForgotPassword(requestRoute: Route) {
  const fetch = fakeApi({
    'GET /csrf': csrfRoute(),
    'GET /me': () => problem(401, 'authentication_required'),
    'POST /password-reset/request': requestRoute,
  })
  render(
    <MemoryRouter initialEntries={['/forgot-password']}>
      <App />
    </MemoryRouter>,
  )
  return fetch
}

function fillAndSubmit(email = 'testuser123@test.example.com') {
  fireEvent.change(screen.getByLabelText('Email'), { target: { value: email } })
  fireEvent.click(screen.getByRole('button', { name: 'Send reset link' }))
}

describe('Forgot-password screen', () => {
  it('shows a Confidential, Sensitive Normal label next to the email field', () => {
    renderForgotPassword(() => new Response(null, { status: 202 }))

    const input = screen.getByLabelText('Email')
    const field = input.closest('.field') as HTMLElement
    expect(within(field).getByText('Confidential / Sensitive Normal')).toBeInTheDocument()
    expect(input).toHaveAccessibleDescription('Confidential / Sensitive Normal')
    expect(input).toHaveAttribute('type', 'email')
  })

  it('posts the email as JSON with the CSRF token and goes to the login page with a generic message', async () => {
    const fetch = renderForgotPassword(() => new Response(null, { status: 202 }))

    fillAndSubmit()

    expect(await screen.findByRole('heading', { name: 'Log in' })).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('If an Account exists for that email')
    const [, init] = fetch.mock.calls.find(([url]) => String(url).endsWith('/password-reset/request'))!
    expect(new Headers(init?.headers).get('Content-Type')).toBe('application/json')
    expect(new Headers(init?.headers).get('X-CSRF-TOKEN')).toBe('token-1')
    expect(init?.credentials).toBe('include')
    expect(JSON.parse(String(init?.body))).toEqual({ email: 'testuser123@test.example.com' })
  })

  it('shows the identical success message whether or not the email is registered', async () => {
    renderForgotPassword(() => new Response(null, { status: 202 }))

    fillAndSubmit('unregistered@test.example.com')

    expect(await screen.findByRole('status')).toHaveTextContent('If an Account exists for that email')
  })

  it.each([
    [problem(429, 'too_many_requests'), 'Too many attempts. Please try again later.'],
    [problem(400, 'validation'), 'Enter a valid email address.'],
    [problem(500, 'internal_error'), 'Something went wrong. Please try again later.'],
  ])('shows a message when the request fails (%#)', async (response, message) => {
    renderForgotPassword(() => response)

    fillAndSubmit()

    const button = screen.getByRole('button', { name: 'Send reset link' })
    await vi.waitFor(() => expect(button).toBeEnabled())
    expect(screen.getByRole('alert')).toHaveTextContent(message)
    expect(screen.getByRole('heading', { name: 'Forgot password' })).toBeInTheDocument()
  })

  it('shows a generic error when the API cannot be reached', async () => {
    renderForgotPassword(() => {
      throw new TypeError('Failed to fetch')
    })

    fillAndSubmit()

    expect(await screen.findByRole('alert')).toHaveTextContent('Something went wrong')
  })

  it('is reachable from the login page and links back to it', async () => {
    fakeApi({ 'GET /csrf': csrfRoute(), 'GET /me': () => problem(401, 'authentication_required') })
    render(
      <MemoryRouter initialEntries={['/login']}>
        <App />
      </MemoryRouter>,
    )

    fireEvent.click(screen.getByRole('link', { name: 'Forgot password?' }))

    expect(await screen.findByRole('heading', { name: 'Forgot password' })).toBeInTheDocument()
    fireEvent.click(screen.getByRole('link', { name: 'Back to login' }))
    expect(await screen.findByRole('heading', { name: 'Log in' })).toBeInTheDocument()
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })
})
