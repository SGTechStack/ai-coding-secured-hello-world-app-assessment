import { beforeEach, describe, expect, it } from 'vitest'
import axios, { AxiosError, AxiosHeaders, type InternalAxiosRequestConfig } from 'axios'
import { api, clearCsrfToken, setSessionLostHandler } from './client'

/**
 * The global interceptor (story 1.23, spec.md S12).
 *
 * One of exactly two named frontend suites, and it exists because **nothing on the backend can test this**.
 * The backend's job ends at returning `403` with a `code`; whether the client inspects that code before
 * deciding to log the user out is invisible from the server, and getting it wrong is the difference between
 * "change your password" and being ejected to the login screen with no explanation.
 *
 * Driven through axios adapters rather than a mock server, because the subject is axios's own interceptor
 * chain — the transport is exactly the part that should be replaced.
 *
 * **Both adapters are stubbed, and that is not redundancy.** The CSRF bootstrap deliberately uses the bare
 * `axios` default rather than the `api` instance, so that the token fetch does not recurse through the
 * request interceptor that is asking for it. Stubbing only `api` leaves the token fetch attempting a real
 * XHR under jsdom, which fails with a network error — and a test asserting "no logout happened" then
 * passes because nothing reached the interceptor at all. Two of these tests did exactly that before the
 * bare adapter was stubbed.
 */

function ok(data: unknown) {
  return (config: InternalAxiosRequestConfig) =>
    Promise.resolve({
      data,
      status: 200,
      statusText: '',
      headers: new AxiosHeaders(),
      config,
    })
}

/** Makes the `api` instance answer one fixed status, rejecting for 4xx the way axios really would. */
function apiAnswers(status: number, data: unknown = {}): void {
  api.defaults.adapter = (config) => {
    const response = { data, status, statusText: '', headers: new AxiosHeaders(), config }
    return status >= 400
      ? Promise.reject(new AxiosError('stub failure', undefined, config, null, response))
      : Promise.resolve(response)
  }
}

describe('the global axios interceptor', () => {
  let sessionLost: number

  beforeEach(() => {
    sessionLost = 0
    setSessionLostHandler(() => {
      sessionLost += 1
    })
    clearCsrfToken()
    // The CSRF bootstrap always succeeds, so no test can pass by accident on a failed token fetch.
    axios.defaults.adapter = ok({
      token: 'tok-abc',
      headerName: 'X-CSRF-TOKEN',
      parameterName: '_csrf',
    })
  })

  it('treats a bare 401 as a lost session', async () => {
    apiAnswers(401)

    await expect(api.get('/hello')).rejects.toThrow()

    expect(sessionLost).toBe(1)
  })

  it('does not log out on a 403 carrying PASSWORD_CHANGE_REQUIRED', async () => {
    // The whole reason the `code` field exists. A user who owes a password change is fully authenticated;
    // logging them out here would make the forced-change flow impossible to complete.
    apiAnswers(403, { code: 'PASSWORD_CHANGE_REQUIRED' })

    await expect(api.get('/users')).rejects.toThrow()

    expect(sessionLost).toBe(0)
  })

  it('does not log out on a 403 carrying SELF_ACTION_NOT_ALLOWED', async () => {
    // An administrator clicking Disable on their own row must stay signed in and see the refusal.
    apiAnswers(403, { code: 'SELF_ACTION_NOT_ALLOWED' })

    await expect(api.patch('/users/self/status', { enabled: false })).rejects.toThrow()

    expect(sessionLost).toBe(0)
  })

  it('does not log out on a 403 carrying ACCESS_DENIED either', async () => {
    // The session is valid; the request was not allowed. Those are different facts and only one of them is
    // a reason to end a session.
    apiAnswers(403, { code: 'ACCESS_DENIED' })

    await expect(api.get('/users')).rejects.toThrow()

    expect(sessionLost).toBe(0)
  })

  it('absorbs a 401 from logout, because it means the session was already gone', async () => {
    // Logout keeps CSRF protection deliberately, so logging out of an expired session answers 401 -- and
    // Std:438 forbids "fixing" that. Reporting it would show an error for the outcome the user asked for.
    apiAnswers(401)

    await expect(api.post('/auth/logout')).rejects.toThrow()

    expect(sessionLost).toBe(0)
  })

  it('absorbs a 401 from the bootstrap currentUser read', async () => {
    // On a cold load a 401 here is the normal answer for "nobody is signed in". Routing on it would put
    // the app in a redirect loop before it had rendered anything.
    apiAnswers(401)

    await expect(api.get('/currentUser')).rejects.toThrow()

    expect(sessionLost).toBe(0)
  })

  it('does not log out on a login 401: that is the credential answer, not a dead session', async () => {
    apiAnswers(401)

    await expect(api.post('/auth/login', { username: 'a', password: 'b' })).rejects.toThrow()

    expect(sessionLost).toBe(0)
  })

  it('attaches a CSRF token to a mutating request, fetching one first', async () => {
    // No endpoint is CSRF-exempt, so this cannot be scoped to authenticated calls: a login with no token
    // is a 403 before it is anything else.
    let sentToken: unknown
    api.defaults.adapter = (config) => {
      sentToken = config.headers.get('X-CSRF-TOKEN')
      return ok({})(config)
    }

    await api.post('/auth/register', { username: 'x' })

    expect(sentToken).toBe('tok-abc')
  })

  it('sends no CSRF header on a GET', async () => {
    let sentToken: unknown = 'unset'
    api.defaults.adapter = (config) => {
      sentToken = config.headers.get('X-CSRF-TOKEN')
      return ok({})(config)
    }

    await api.get('/hello')

    // AxiosHeaders.get returns undefined for a header that was never set on this instance.
    expect(sentToken).toBeUndefined()
  })

  it('uses the header name the server returned rather than a hard-coded one', async () => {
    // CsrfController returns headerName precisely so no client has to guess it. A client that hard-codes
    // it keeps working until the day the backend changes it, and then fails as a blanket 403.
    axios.defaults.adapter = ok({
      token: 'tok-xyz',
      headerName: 'X-Renamed-Csrf',
      parameterName: '_csrf',
    })
    let renamed: unknown
    api.defaults.adapter = (config) => {
      renamed = config.headers.get('X-Renamed-Csrf')
      return ok({})(config)
    }

    await api.post('/auth/register', { username: 'x' })

    expect(renamed).toBe('tok-xyz')
  })
})
