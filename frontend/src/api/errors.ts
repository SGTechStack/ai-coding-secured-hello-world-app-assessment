/**
 * Shared error vocabulary for every backend call.
 *
 * Every `ApiError` carries a *fixed, client-owned* message that is safe to
 * render as-is. Server response bodies are never surfaced verbatim, with one
 * deliberate exception: the `message` of a 400 `VALIDATION_FAILED` on the
 * register / password-reset endpoints, which is a fixed string produced by the
 * backend's own bean-validation annotations (see `validationMessage`). Even
 * that is only ever rendered as a React text node, never as HTML.
 */

export const MESSAGES = {
  serverUnavailable: 'Unable to connect to the server. Please try again later.',
  rateLimited: 'Too many attempts. Please try again later.',
  forbidden: "You don't have permission to perform this action.",
  unauthenticated: 'Your session has expired. Please log in again.',
  notFound: 'That item no longer exists.',
  invalidCredentials: 'Invalid username or password',
  registrationConflict: 'Username or email is already registered',
  registrationInvalid: 'Please check your details and try again.',
  resetInvalidToken: 'This password reset link is invalid or has expired. Please request a new one.',
  resetInvalid: 'Unable to reset your password. Please check your input and try again.',
  resetRequestInvalid: 'Please enter a valid email address.',
  adminSelfAction: "You can't perform this action on your own account.",
  adminLastAdmin: 'At least one enabled administrator must remain.',
  adminActionFailed: 'That action could not be completed.',
  logoutFailed: 'Unable to log out. Please try again.',
} as const;

/** Base class: `message` is always safe to show to the user. */
export class ApiError extends Error {}

/** The server returned a 5xx / unexpected status, or the request never reached it at all. */
export class ServerUnavailableError extends ApiError {
  constructor(message: string = MESSAGES.serverUnavailable) {
    super(message);
  }
}

/** HTTP 429. */
export class RateLimitedError extends ApiError {
  constructor() {
    super(MESSAGES.rateLimited);
  }
}

/** HTTP 401 on a protected endpoint: the session is gone. */
export class UnauthenticatedError extends ApiError {
  constructor() {
    super(MESSAGES.unauthenticated);
  }
}

/** HTTP 403 (after the one CSRF refetch-and-retry in `apiFetch`). */
export class ForbiddenError extends ApiError {
  constructor() {
    super(MESSAGES.forbidden);
  }
}

/** HTTP 404. */
export class NotFoundError extends ApiError {
  constructor() {
    super(MESSAGES.notFound);
  }
}

/** Maps a non-2xx status to the generic error for that status class. */
export function errorForStatus(status: number): ApiError {
  switch (status) {
    case 401:
      return new UnauthenticatedError();
    case 403:
      return new ForbiddenError();
    case 404:
      return new NotFoundError();
    case 429:
      return new RateLimitedError();
    default:
      return new ServerUnavailableError();
  }
}

export interface ErrorBody {
  code?: string;
  message?: string;
}

/** Parses the `{code, message}` error body; never throws (a malformed body yields `{}`). */
export async function readErrorBody(response: Response): Promise<ErrorBody> {
  try {
    const body: unknown = await response.json();
    if (body && typeof body === 'object') {
      const { code, message } = body as Record<string, unknown>;
      return {
        code: typeof code === 'string' ? code : undefined,
        message: typeof message === 'string' ? message : undefined,
      };
    }
  } catch {
    // Fall through: non-JSON / empty body.
  }
  return {};
}

// Upper bound on a displayed validation message; anything longer is not one
// of our own annotation messages and falls back to the fixed client string.
const MAX_VALIDATION_MESSAGE_LENGTH = 200;

/**
 * Returns the server's message only for a 400 `VALIDATION_FAILED` body (a
 * fixed string from the backend's own validation annotations), else `fallback`.
 */
export function validationMessage(body: ErrorBody, fallback: string): string {
  if (
    body.code === 'VALIDATION_FAILED' &&
    body.message &&
    body.message.length <= MAX_VALIDATION_MESSAGE_LENGTH
  ) {
    return body.message;
  }
  return fallback;
}

/** Message to display for any thrown value: the `ApiError`'s own fixed message, else the generic one. */
export function messageFor(error: unknown): string {
  return error instanceof ApiError ? error.message : MESSAGES.serverUnavailable;
}
