/**
 * The closed error-code enum, mirrored from the backend's `ErrorCode` (ADR-031). A Vitest test compares this list with
 * the generated `docs/api/error-contract.schema.json`, so the two cannot drift apart.
 */
export const ERROR_CODES = [
  'AUTHENTICATION_FAILED',
  'PASSWORD_CHANGE_REQUIRED',
  'CSRF_TOKEN_INVALID',
  'ACCESS_DENIED',
  'VALIDATION_FAILED',
  'PASSWORD_REJECTED',
  'RESET_TOKEN_INVALID',
  'USER_EXISTS',
  'TOO_MANY_REQUESTS',
  'MISSING_FACTOR',
  'INVALID_FACTOR',
  'FACTOR_ENROLMENT_REQUIRED',
  'FACTOR_ALREADY_ENROLLED',
  'FACTOR_DISABLED',
  'INTERNAL_ERROR',
] as const

export type ErrorCode = (typeof ERROR_CODES)[number]

/** The RFC 9457 envelope every API error carries. Only `code` is branched on (REJ-092). */
export interface Problem {
  type: string
  title: string
  status: number
  detail: string
  instance: string
  traceId: string
  code: ErrorCode
  [extension: string]: unknown
}

const codes: ReadonlySet<string> = new Set(ERROR_CODES)

export function isErrorCode(value: unknown): value is ErrorCode {
  return typeof value === 'string' && codes.has(value)
}

export function isProblem(value: unknown): value is Problem {
  return typeof value === 'object' && value !== null && isErrorCode((value as { code?: unknown }).code)
}

/** An API error response in the envelope. Callers branch on `code`, never on `status`, `title` or `detail`. */
export class ApiError extends Error {
  readonly code: ErrorCode
  readonly problem: Problem
  /** The integer `Retry-After` in seconds, when the response carried one (every 429 does; ADR-014). */
  readonly retryAfterSeconds: number | undefined

  constructor(problem: Problem, retryAfterSeconds?: number) {
    super(problem.code)
    this.name = 'ApiError'
    this.code = problem.code
    this.problem = problem
    this.retryAfterSeconds = retryAfterSeconds
  }
}

/** A `Retry-After` header's whole seconds, or `undefined` when it is absent or not a non-negative integer. */
export function retryAfterSeconds(header: string | null): number | undefined {
  return header !== null && /^\d+$/.test(header) ? Number(header) : undefined
}

/**
 * A response with a status but no envelope: something between the SPA and the API answered instead (a proxy, a
 * static host). It is not retried; only a request that got no status is (REJ-052).
 */
export class UnexpectedResponseError extends Error {
  readonly status: number

  constructor(status: number) {
    super(`Unexpected response with status ${status}`)
    this.name = 'UnexpectedResponseError'
    this.status = status
  }
}
