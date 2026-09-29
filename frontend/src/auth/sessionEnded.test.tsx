import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { json, mockApi, problem } from '../test/mockApi'
import { renderApp } from '../test/renderApp'

describe('ended sessions', () => {
  it('send the user to sign in with a notice when any API call finds the session gone', async () => {
    mockApi({
      'GET /api/me': () => json(200, { id: 'u1', username: 'alice', role: 'USER' }),
      // e.g. an admin disabled the account, or the password was reset elsewhere
      'GET /api/hello': () => problem(401, 'UNAUTHENTICATED', 'Authentication is required.'),
    })

    renderApp('/')

    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('Your session has ended. Please sign in again.')
  })
})
