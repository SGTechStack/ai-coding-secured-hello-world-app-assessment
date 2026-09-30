import { ApiError, ErrorCode } from '../api/client';

/** What to tell a Throttled user, saying when they may try again if the API did; undefined for any other error. */
export function throttledMessage(error: unknown): string | undefined {
  if (!(error instanceof ApiError && error.code === ErrorCode.tooManyRequests)) return undefined;
  const seconds = error.problem.retryAfterSeconds;
  if (seconds === undefined) return 'Too many attempts. Please try again later.';
  return `Too many attempts. Try again in ${seconds} ${seconds === 1 ? 'second' : 'seconds'}.`;
}
