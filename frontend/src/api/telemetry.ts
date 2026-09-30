/**
 * Client-side error and performance reporting (story 111, IM8 lm-16). The SPA has no operator-visible
 * destination of its own, and its CSP only lets it reach the API origin, so every report goes to
 * `POST /api/client-events`, which counts it on the Actuator management port and writes one line to
 * the application log.
 *
 * A report carries the event kind, the page path and — for a performance report — a duration. It never
 * carries a message, a stack trace, an exception, a form value or anything else about the person using
 * the app: the kind is the whole description of what went wrong. Reports are best-effort and never
 * surface to the user, and no page sends more than {@link MAX_ERROR_REPORTS_PER_PAGE} error reports,
 * so a render loop cannot flood the log.
 *
 * Unlike every other call the SPA makes, a report does not go through `apiRequest`: it carries no
 * credentials and no CSRF token (ADR 0002 records the exemption). Reporting starts at page load,
 * before anyone has interacted with the app, and fetching a CSRF token would make the server create
 * and persist a Session for every Visitor and every crawler. A report needs no Session, so it sends
 * none — which also means a report can never be tied to an Account.
 */

const API_BASE = `${import.meta.env.VITE_API_ORIGIN}/api`

/** What the SPA reports. Mirrors the API's `ClientEventKind`; nothing else is accepted. */
export type ClientEventKind =
  'RENDER_ERROR' | 'UNCAUGHT_ERROR' | 'UNHANDLED_REJECTION' | 'TIME_TO_FIRST_BYTE' | 'PAGE_LOAD'

export type ClientErrorKind = Extract<ClientEventKind, 'RENDER_ERROR' | 'UNCAUGHT_ERROR' | 'UNHANDLED_REJECTION'>

/** At most this many error reports per loaded page, so a repeating failure cannot flood the API. */
export const MAX_ERROR_REPORTS_PER_PAGE = 10

/** The API accepts only this shape of path; anything else is reported as {@link OTHER_PATH}. */
const SAFE_PATH = /^\/[A-Za-z0-9/_-]{0,63}$/
const OTHER_PATH = '/other'

let errorReportsSent = 0

/**
 * Reports one client-side error. Resolves whether or not the report was accepted: a failed report is
 * never worth telling the user about, and must never become an error of its own.
 */
export async function reportClientError(kind: ClientErrorKind): Promise<void> {
  if (errorReportsSent >= MAX_ERROR_REPORTS_PER_PAGE) return
  errorReportsSent += 1
  await send(kind)
}

/**
 * Reports how long this page took to load, from the browser's Navigation Timing entry: time to the
 * first response byte, and time to the load event. Reports nothing when the browser has no entry, or
 * for a mark the browser has not set yet.
 *
 * Must not be called from inside a `load` handler: `loadEventEnd` is only set once every handler has
 * returned, so it would still read 0 there. {@link startClientReporting} defers the call by a task
 * for exactly that reason.
 */
export async function reportPagePerformance(): Promise<void> {
  const [navigation] = performance.getEntriesByType('navigation') as PerformanceNavigationTiming[]
  if (!navigation) return
  await Promise.all([
    durationOf(navigation.responseStart, 'TIME_TO_FIRST_BYTE'),
    durationOf(navigation.loadEventEnd, 'PAGE_LOAD'),
  ])
}

function durationOf(endMark: number, kind: ClientEventKind): Promise<void> {
  // Navigation Timing leaves a mark at 0 until the event it measures has happened.
  return endMark > 0 ? send(kind, Math.round(endMark)) : Promise.resolve()
}

/**
 * Starts reporting: errors that reach the window (including a rejected promise nothing handled) and,
 * once the page has loaded, its load timings. Render errors are reported by the `ErrorBoundary`, which
 * stops them from reaching the window. Returns a function that stops reporting again.
 */
export function startClientReporting(): () => void {
  const onError = () => void reportClientError('UNCAUGHT_ERROR')
  const onRejection = () => void reportClientError('UNHANDLED_REJECTION')
  // A task later, not in the handler: the browser sets loadEventEnd only once every load handler has
  // returned, so reading it here would report 0 and drop the PAGE_LOAD timing altogether.
  let pendingPerformance: ReturnType<typeof setTimeout> | undefined
  const onLoad = () => {
    pendingPerformance = setTimeout(() => void reportPagePerformance(), 0)
  }

  window.addEventListener('error', onError)
  window.addEventListener('unhandledrejection', onRejection)
  if (document.readyState === 'complete') {
    onLoad()
  } else {
    window.addEventListener('load', onLoad)
  }

  return () => {
    window.removeEventListener('error', onError)
    window.removeEventListener('unhandledrejection', onRejection)
    window.removeEventListener('load', onLoad)
    clearTimeout(pendingPerformance)
  }
}

async function send(kind: ClientEventKind, durationMs?: number): Promise<void> {
  const path = window.location.pathname
  const body = { kind, path: SAFE_PATH.test(path) ? path : OTHER_PATH, durationMs }
  try {
    // No credentials and no CSRF token, so the server never creates a Session for a report.
    await fetch(`${API_BASE}/client-events`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
      credentials: 'omit',
    })
  } catch {
    // Reporting is best-effort: a failed report must not become an error of its own.
  }
}
