import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { API, fakeApi } from '../test/fakeApi'

// The reporter counts error reports at module level, so each test gets a fresh copy.
let telemetry: typeof import('./telemetry')
beforeEach(async () => {
  vi.resetModules()
  telemetry = await import('./telemetry')
})

afterEach(() => window.history.replaceState({}, '', '/'))

const accepted = () => new Response(null, { status: 204 })
const reports = (fetch: ReturnType<typeof fakeApi>) =>
  fetch.mock.calls.filter(([, init]) => init?.method === 'POST').map(([, init]) => JSON.parse(String(init?.body)))
const kindsOf = (fetch: ReturnType<typeof fakeApi>) => reports(fetch).map((report) => report.kind)

const reportingApi = () => fakeApi({ 'POST /client-events': accepted })

/** The two Navigation Timing marks the reporter reads. Mutable, unlike the browser's own entry. */
type Marks = { responseStart: number; loadEventEnd: number }

/**
 * Stands in for the browser's Navigation Timing entry. Returned mutable so a test can move a mark the
 * way the browser does — `loadEventEnd` in particular is set only after every `load` handler returns.
 */
function navigationTiming(marks: Partial<Marks>): Marks {
  const entry: Marks = { responseStart: 0, loadEventEnd: 0, ...marks }
  vi.spyOn(performance, 'getEntriesByType').mockImplementation((type) =>
    type === 'navigation' ? [entry as unknown as PerformanceNavigationTiming] : [],
  )
  return entry
}

describe('client error reporting', () => {
  it('reports the kind and page path, and nothing else', async () => {
    const fetch = reportingApi()

    await telemetry.reportClientError('RENDER_ERROR')

    expect(reports(fetch)).toEqual([{ kind: 'RENDER_ERROR', path: '/' }])
    expect(fetch.mock.calls.at(-1)?.[0]).toBe(`${API}/client-events`)
  })

  it('sends no credentials and no CSRF token, so the server creates no Session for a report', async () => {
    const fetch = reportingApi()

    await telemetry.reportClientError('RENDER_ERROR')

    const [, init] = fetch.mock.calls.at(-1)!
    expect(init?.credentials).toBe('omit')
    expect(new Headers(init?.headers).has('X-CSRF-TOKEN')).toBe(false)
    // Nothing bootstrapped a token either: that request is what would have created the Session.
    expect(fetch.mock.calls.map(([url]) => String(url))).not.toContain(`${API}/csrf`)
  })

  it('reports a page path the API would refuse as a fixed placeholder', async () => {
    const fetch = reportingApi()
    window.history.replaceState({}, '', '/pages/whoever@example.invalid')

    await telemetry.reportClientError('UNCAUGHT_ERROR')

    expect(reports(fetch)).toEqual([{ kind: 'UNCAUGHT_ERROR', path: '/other' }])
  })

  it('swallows a refused report rather than letting it become an error of its own', async () => {
    fakeApi({ 'POST /client-events': () => new Response(null, { status: 429 }) })

    await expect(telemetry.reportClientError('RENDER_ERROR')).resolves.toBeUndefined()
  })

  it('swallows a report that cannot reach the network', async () => {
    vi.spyOn(globalThis, 'fetch').mockRejectedValue(new Error('offline'))

    await expect(telemetry.reportClientError('RENDER_ERROR')).resolves.toBeUndefined()
  })

  it('stops reporting after the per-page budget, so a render loop cannot flood the log', async () => {
    const fetch = reportingApi()

    for (let i = 0; i < telemetry.MAX_ERROR_REPORTS_PER_PAGE + 5; i++) {
      await telemetry.reportClientError('RENDER_ERROR')
    }

    expect(reports(fetch)).toHaveLength(telemetry.MAX_ERROR_REPORTS_PER_PAGE)
  })
})

describe('page performance reporting', () => {
  it('reports the time to the first response byte and to the load event, rounded to whole milliseconds', async () => {
    const fetch = reportingApi()
    navigationTiming({ responseStart: 12.4, loadEventEnd: 987.6 })

    await telemetry.reportPagePerformance()

    expect(reports(fetch)).toEqual([
      { kind: 'TIME_TO_FIRST_BYTE', path: '/', durationMs: 12 },
      { kind: 'PAGE_LOAD', path: '/', durationMs: 988 },
    ])
  })

  it('reports only the marks the browser has actually set', async () => {
    const fetch = reportingApi()
    navigationTiming({ responseStart: 20, loadEventEnd: 0 })

    await telemetry.reportPagePerformance()

    expect(reports(fetch)).toEqual([{ kind: 'TIME_TO_FIRST_BYTE', path: '/', durationMs: 20 }])
  })

  it('reports nothing when the browser has no navigation entry', async () => {
    const fetch = reportingApi()
    vi.spyOn(performance, 'getEntriesByType').mockReturnValue([])

    await telemetry.reportPagePerformance()

    expect(reports(fetch)).toEqual([])
  })
})

describe('startClientReporting', () => {
  it('reports an error that reaches the window and a promise nothing handled, and stops when told to', async () => {
    const fetch = reportingApi()
    navigationTiming({})

    const stop = telemetry.startClientReporting()
    window.dispatchEvent(new Event('error'))
    window.dispatchEvent(new Event('unhandledrejection'))
    await vi.waitUntil(() => reports(fetch).length === 2)
    stop()
    window.dispatchEvent(new Event('error'))
    await Promise.resolve()

    expect(kindsOf(fetch)).toEqual(['UNCAUGHT_ERROR', 'UNHANDLED_REJECTION'])
  })

  it('reports the load timing after the load handlers have returned, when the browser has set the mark', async () => {
    const fetch = reportingApi()
    // Before the load event the browser has a response time but no load time.
    const entry = navigationTiming({ responseStart: 5 })
    vi.spyOn(document, 'readyState', 'get').mockReturnValue('loading')

    const stop = telemetry.startClientReporting()
    window.dispatchEvent(new Event('load'))
    // Only now, with every load handler returned, does the browser set loadEventEnd.
    entry.loadEventEnd = 50
    await vi.waitUntil(() => reports(fetch).length === 2)
    stop()

    expect(reports(fetch)).toEqual([
      { kind: 'TIME_TO_FIRST_BYTE', path: '/', durationMs: 5 },
      { kind: 'PAGE_LOAD', path: '/', durationMs: 50 },
    ])
  })

  it('reports page performance when the page had already loaded before reporting started', async () => {
    const fetch = reportingApi()
    navigationTiming({ responseStart: 5, loadEventEnd: 50 })
    vi.spyOn(document, 'readyState', 'get').mockReturnValue('complete')

    const stop = telemetry.startClientReporting()
    await vi.waitUntil(() => reports(fetch).length === 2)
    stop()

    expect(kindsOf(fetch)).toEqual(['TIME_TO_FIRST_BYTE', 'PAGE_LOAD'])
  })

  it('drops a load timing that has not been read by the time reporting stops', async () => {
    const fetch = reportingApi()
    navigationTiming({ responseStart: 5, loadEventEnd: 50 })
    vi.spyOn(document, 'readyState', 'get').mockReturnValue('loading')

    const stop = telemetry.startClientReporting()
    window.dispatchEvent(new Event('load'))
    stop()
    await new Promise((resolve) => setTimeout(resolve, 5))

    expect(reports(fetch)).toEqual([])
  })
})
