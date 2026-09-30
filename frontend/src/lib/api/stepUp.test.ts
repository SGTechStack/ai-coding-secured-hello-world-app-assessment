import { http, HttpResponse } from 'msw'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { problemResponse } from '@/test/msw/problems'
import { server } from '@/test/msw/server'
import { apiFetch, apiUrl, clearCsrfToken, CSRF_HEADER, refreshCsrfToken } from './client'
import { ApiError } from './errors'
import { abandonStepUp, completeStepUp, isStepUpPending, StepUpCancelledError, subscribeStepUp } from './stepUp'

const ENABLED = '/api/admin/users/:id/enabled'

/** Serves `token-1`, `token-2`, ... one per bootstrap, as the server rotates it with the session id. */
function tokenServer() {
  let bootstraps = 0
  server.use(
    http.get(apiUrl('/api/csrf'), () => {
      bootstraps += 1
      return HttpResponse.json({ headerName: CSRF_HEADER, token: `token-${bootstraps}` })
    }),
  )
}

/** The enable route: refused with a stale factor until `fresh`, then accepted. Records every attempt's id and token. */
function enabledRoute() {
  const state = { fresh: false, attempts: [] as { id: string; token: string | null }[] }
  server.use(
    http.put(apiUrl(ENABLED), ({ params, request }) => {
      state.attempts.push({ id: String(params.id), token: request.headers.get(CSRF_HEADER) })
      return state.fresh
        ? HttpResponse.json({ id: params.id })
        : problemResponse('MISSING_FACTOR', `/api/admin/users/${String(params.id)}/enabled`, {
            factor: 'TOTP',
            reason: 'EXPIRED',
          })
    }),
  )
  return state
}

const disable = (id: string) =>
  apiFetch<{ id: string }>(`/api/admin/users/${id}/enabled`, { method: 'PUT', body: '{}' })

/** Waits until the challenge is open, as the dialog would see it. */
async function challengeOpened(): Promise<void> {
  await expect.poll(isStepUpPending).toBe(true)
}

let opened: number
let unsubscribe: () => void

beforeEach(() => {
  clearCsrfToken()
  tokenServer()
  opened = 0
  // Stands in for the mounted challenge: counts how often the queue asks for it.
  let wasOpen = false
  unsubscribe = subscribeStepUp(() => {
    if (isStepUpPending() && !wasOpen) {
      opened += 1
    }
    wasOpen = isStepUpPending()
  })
})

afterEach(() => unsubscribe())

describe('step-up queue', () => {
  it('two concurrent mutations refused with MISSING_FACTOR open one challenge', async () => {
    const route = enabledRoute()

    const first = disable('a')
    const second = disable('b')
    await challengeOpened()
    await expect.poll(() => route.attempts.length).toBe(2)

    expect(opened).toBe(1)
    route.fresh = true
    completeStepUp()
    await Promise.all([first, second])
  })

  it('after the step-up both are replayed exactly once, with the token fetched after the rotation', async () => {
    const route = enabledRoute()
    const first = disable('a')
    const second = disable('b')
    await challengeOpened()
    await expect.poll(() => route.attempts.length).toBe(2)

    // What a verified code does: the session id rotated, so the token is fetched again before anything replays.
    route.fresh = true
    await refreshCsrfToken()
    completeStepUp()

    expect(await first).toEqual({ id: 'a' })
    expect(await second).toEqual({ id: 'b' })
    expect(route.attempts).toEqual([
      { id: 'a', token: 'token-1' },
      { id: 'b', token: 'token-1' },
      { id: 'a', token: 'token-2' },
      { id: 'b', token: 'token-2' },
    ])
    expect(isStepUpPending()).toBe(false)
  })

  it('a dismissed challenge rejects the queued requests without replaying them', async () => {
    const route = enabledRoute()
    const first = disable('a')
    const second = disable('b')
    await challengeOpened()
    await expect.poll(() => route.attempts.length).toBe(2)

    abandonStepUp()

    await expect(first).rejects.toBeInstanceOf(StepUpCancelledError)
    await expect(second).rejects.toBeInstanceOf(StepUpCancelledError)
    expect(route.attempts).toHaveLength(2)
    expect(isStepUpPending()).toBe(false)
  })

  it('a terminal refusal of the code fails the queued requests with that refusal', async () => {
    enabledRoute()
    const first = disable('a')
    await challengeOpened()
    const refusal = new ApiError({
      type: 'about:blank',
      title: 'Factor disabled',
      status: 423,
      detail: '',
      instance: '/api/mfa/totp/verification',
      traceId: 't',
      code: 'FACTOR_DISABLED',
    })

    abandonStepUp(refusal)

    await expect(first).rejects.toBe(refusal)
  })

  it('a replay refused again is returned to the caller, not queued a second time', async () => {
    const route = enabledRoute()
    const first = disable('a')
    await challengeOpened()

    completeStepUp()

    await expect(first).rejects.toMatchObject({ code: 'MISSING_FACTOR' })
    expect(route.attempts).toHaveLength(2)
    expect(opened).toBe(1)
    expect(isStepUpPending()).toBe(false)
  })

  it('without a mounted challenge the refusal is returned as it came', async () => {
    unsubscribe()
    const route = enabledRoute()

    await expect(disable('a')).rejects.toMatchObject({ code: 'MISSING_FACTOR' })
    expect(route.attempts).toHaveLength(1)
    expect(isStepUpPending()).toBe(false)
  })

  it('a read refused with MISSING_FACTOR is never queued: a read needs no fresh factor', async () => {
    server.use(http.get(apiUrl('/api/admin/users'), () => problemResponse('MISSING_FACTOR', '/api/admin/users')))

    await expect(apiFetch('/api/admin/users')).rejects.toMatchObject({ code: 'MISSING_FACTOR' })
    expect(isStepUpPending()).toBe(false)
  })

  it('the challenge unmounting with requests queued drops them', async () => {
    const route = enabledRoute()
    const first = disable('a')
    await challengeOpened()

    unsubscribe()

    await expect(first).rejects.toBeInstanceOf(StepUpCancelledError)
    expect(route.attempts).toHaveLength(1)
  })

  it('settling with nothing queued does nothing', () => {
    completeStepUp()
    abandonStepUp()

    expect(isStepUpPending()).toBe(false)
    expect(opened).toBe(0)
  })
})
