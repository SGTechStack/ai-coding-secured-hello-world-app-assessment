/**
 * The step-up queue (spec, Frontend; CONTEXT.md): the admin requests refused because the factor was stale, waiting on
 * one challenge. Client-side and single-flight: however many requests are refused, one challenge opens and one code
 * settles them all. The server keeps no copy.
 *
 * {@link apiFetch} queues a refused request with {@link awaitStepUp}; the mounted challenge watches
 * {@link subscribeStepUp} and {@link isStepUpPending}, and settles the queue with {@link completeStepUp} after a
 * verified code, or {@link abandonStepUp} when it is dismissed or the code meets a terminal refusal.
 *
 * @see apiFetch
 */

/** What each queued request rejects with when the challenge is dismissed: nothing was replayed (T-FE-014). */
export class StepUpCancelledError extends Error {
  constructor() {
    super('The step-up was cancelled')
    this.name = 'StepUpCancelledError'
  }
}

interface Pending {
  promise: Promise<void>
  resolve: () => void
  reject: (reason: unknown) => void
}

let pending: Pending | undefined
const listeners = new Set<() => void>()

/**
 * The step-up every refused request waits on: the open one, or a new one that opens the challenge. Undefined when no
 * challenge is mounted to answer it, so the caller returns the refusal as it came.
 */
export function awaitStepUp(): Promise<void> | undefined {
  if (listeners.size === 0) {
    return undefined
  }
  if (!pending) {
    let resolve!: () => void
    let reject!: (reason: unknown) => void
    const promise = new Promise<void>((onResolve, onReject) => {
      resolve = onResolve
      reject = onReject
    })
    pending = { promise, resolve, reject }
    notify()
  }
  return pending.promise
}

/** Whether a step-up is waiting on a code: the challenge is open. */
export function isStepUpPending(): boolean {
  return pending !== undefined
}

/**
 * The code was verified: every queued request is replayed, once. Call it only after the CSRF token has been fetched
 * again, since the grant rotated the session id and the token with it (ADR-040).
 */
export function completeStepUp(): void {
  settle((settled) => settled.resolve())
}

/**
 * Drops the queue: every queued request rejects with `reason` and none is replayed. By default the challenge was
 * dismissed ({@link StepUpCancelledError}); a terminal refusal of the code passes the refusal on, so each page follows
 * the server's code.
 */
export function abandonStepUp(reason: unknown = new StepUpCancelledError()): void {
  settle((settled) => settled.reject(reason))
}

/**
 * Registers the challenge; returns the unsubscribe. While nothing is registered, refused requests are not queued. The
 * last challenge leaving drops whatever is still queued.
 */
export function subscribeStepUp(listener: () => void): () => void {
  listeners.add(listener)
  return () => {
    listeners.delete(listener)
    if (listeners.size === 0) {
      abandonStepUp()
    }
  }
}

function settle(action: (settled: Pending) => void): void {
  const settled = pending
  if (!settled) {
    return
  }
  pending = undefined
  action(settled)
  notify()
}

function notify(): void {
  for (const listener of listeners) {
    listener()
  }
}
