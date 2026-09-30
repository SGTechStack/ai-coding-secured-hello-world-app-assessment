import { ApiError } from '../api/client';

/** A user-facing sentence for any thrown value. Never leaks stack traces. */
export function describeError(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 429) {
      return error.problem.detail ?? 'Too many attempts. Please wait and try again.';
    }
    if (error.status >= 500) {
      return 'The server had a problem. Please try again in a moment.';
    }
    return error.problem.detail ?? error.message;
  }
  if (error instanceof TypeError) {
    return 'Could not reach the server. Is the backend running?';
  }
  if (error instanceof Error) {
    return error.message;
  }
  return 'Something went wrong.';
}

/** Field-level messages keyed by field name, or an empty object. */
export function fieldErrorsOf(error: unknown): Record<string, string> {
  return error instanceof ApiError ? error.fieldErrors : {};
}
