import { ApiError } from './api/client'

/** True when the API said there is no valid session; AuthProvider then sends the user to sign in. */
export function endsSession(error: unknown): boolean {
  return error instanceof ApiError && error.code === 'UNAUTHENTICATED'
}

/** A user-facing message for a failed call. Server messages are shown as text, never as HTML. */
export function describeError(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 429) {
      const minutes = error.retryAfterSeconds ? Math.ceil(error.retryAfterSeconds / 60) : undefined
      return minutes
        ? `Too many attempts. Try again in ${minutes} minute${minutes === 1 ? '' : 's'}.`
        : 'Too many attempts. Try again later.'
    }
    if (error.status >= 500) {
      return 'Something went wrong on our side. Please try again.'
    }
    return error.message
  }
  return 'Could not reach the server. Check your connection and try again.'
}
