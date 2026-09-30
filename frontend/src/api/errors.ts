/**
 * The machine-readable `code` on every error body (spec.md S10).
 *
 * Mirrors the backend's `ApiErrorCode` enum. It is duplicated rather than generated because the set is
 * small, stable and part of the HTTP contract — and because a generator would hide the one thing that
 * matters here, which is that the SPA acts on three of these values specifically.
 */
export type ApiErrorCode =
  | 'PASSWORD_CHANGE_REQUIRED'
  | 'ACCESS_DENIED'
  | 'SELF_ACTION_NOT_ALLOWED'
  | 'LAST_USER_MANAGER'
  | 'CURRENT_PASSWORD_INVALID'
  | 'VALIDATION_FAILED'
  | 'IDENTIFIER_UNAVAILABLE'
  | 'RESET_TOKEN_INVALID'
  | 'RATE_LIMITED'
  | 'USER_NOT_FOUND'
  | 'INTERNAL_ERROR'

/** An RFC 9457 problem document as this application writes it. */
export interface ProblemDetail {
  type?: string
  title?: string
  status?: number
  detail?: string
  code?: ApiErrorCode
}

/**
 * The 403 codes that mean "show this in the app", as opposed to "the session is gone".
 *
 * This list is the entire reason the `code` field exists. Without it a 403 is ambiguous: it could be a
 * dead session, a forced password change, or an administrator trying to act on their own account — and
 * an interceptor that logged out on every 403 would eject a user for a mistake they can fix in place
 * (Std:438, ticket 21).
 */
export const IN_APP_FORBIDDEN_CODES: readonly ApiErrorCode[] = [
  'PASSWORD_CHANGE_REQUIRED',
  'SELF_ACTION_NOT_ALLOWED',
]

/** The user-facing message for a failed call, falling back to something honest but not alarming. */
export function messageFor(problem: ProblemDetail | undefined, fallback: string): string {
  return problem?.detail?.trim() || fallback
}
