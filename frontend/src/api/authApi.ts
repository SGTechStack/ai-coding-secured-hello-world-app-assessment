/**
 * Thin client around the `/api/auth/*` endpoints.
 *
 * Deliberately not mocked in component tests: Seam 2 (frontend component
 * seam, see spec.md "Testing Decisions") mocks `fetch` itself, so this module
 * stays a plain wrapper with no behavior of its own to hide.
 */
import { apiFetch, clearCsrfToken } from './apiClient';
import {
  ApiError,
  MESSAGES,
  RateLimitedError,
  ServerUnavailableError,
  errorForStatus,
  readErrorBody,
  validationMessage,
} from './errors';

export { ServerUnavailableError } from './errors';

export interface LoginCredentials {
  username: string;
  password: string;
}

export type Role = 'USER' | 'ADMIN';

/** The session user, as returned by `GET /api/auth/me`. */
export interface AuthenticatedUser {
  username: string;
  email: string;
  role: Role;
}

/** Credentials were rejected (HTTP 401). Message is intentionally generic. */
export class InvalidCredentialsError extends ApiError {
  constructor() {
    super(MESSAGES.invalidCredentials);
  }
}

export async function login({ username, password }: LoginCredentials): Promise<void> {
  let response: Response;
  try {
    response = await apiFetch('/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password }),
    });
  } catch {
    throw new ServerUnavailableError();
  }

  if (response.ok) {
    // The server rotates the CSRF token on authentication.
    clearCsrfToken();
    return;
  }

  if (response.status === 401) {
    // The server's 401 is identical for every failure cause; the client
    // message is hardcoded so no field-specific detail can leak through.
    throw new InvalidCredentialsError();
  }

  if (response.status === 429) {
    throw new RateLimitedError();
  }

  throw errorForStatus(response.status);
}

/**
 * Returns the current session user, or `null` when there is no valid
 * session (no cookie, expired/unknown cookie, or the request itself
 * failed). Used both for session-restore-on-load and by the route guards —
 * neither cares *why* there's no session, so failures collapse to `null`
 * rather than a thrown error.
 */
export async function getMe(): Promise<AuthenticatedUser | null> {
  let response: Response;
  try {
    response = await apiFetch('/api/auth/me');
  } catch {
    return null;
  }

  if (!response.ok) {
    return null;
  }

  return (await response.json()) as AuthenticatedUser;
}

/**
 * Invalidates the session. Throws when the server could not be reached or
 * refused, so the caller can tell the user they are *not* logged out. A 401
 * means the session was already gone, which counts as success.
 */
export async function logout(): Promise<void> {
  let response: Response;
  try {
    response = await apiFetch('/api/auth/logout', { method: 'POST' });
  } catch {
    throw new ServerUnavailableError(MESSAGES.logoutFailed);
  } finally {
    // Whatever the outcome, the old token is either destroyed or suspect.
    clearCsrfToken();
  }

  if (response.ok || response.status === 401) {
    return;
  }

  throw new ServerUnavailableError(MESSAGES.logoutFailed);
}

export interface RegisterInput {
  username: string;
  email: string;
  password: string;
  firstName: string;
}

/**
 * Registration was rejected: 400 (validation; the fixed annotation message is
 * shown) or 409 (a single merged conflict message, spec D6).
 */
export class RegistrationError extends ApiError {}

export async function register({ username, email, password, firstName }: RegisterInput): Promise<void> {
  let response: Response;
  try {
    response = await apiFetch('/api/auth/register', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, email, password, firstName }),
    });
  } catch {
    throw new ServerUnavailableError();
  }

  if (response.status === 201) {
    return;
  }

  if (response.status === 400) {
    const body = await readErrorBody(response);
    throw new RegistrationError(validationMessage(body, MESSAGES.registrationInvalid));
  }

  if (response.status === 409) {
    throw new RegistrationError(MESSAGES.registrationConflict);
  }

  throw errorForStatus(response.status);
}
