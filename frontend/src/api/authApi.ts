/**
 * Thin client around `POST /api/auth/login`.
 *
 * Deliberately not mocked in component tests: Seam 2 (frontend component
 * seam, see spec.md "Testing Decisions") mocks `fetch` itself, so this module
 * stays a plain wrapper with no behavior of its own to hide.
 */
import { apiFetch } from './apiClient';

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
export class InvalidCredentialsError extends Error {}

/** This client is being throttled after repeated failed logins (HTTP 429). */
export class TooManyAttemptsError extends Error {}

/** The server returned a 5xx, or the request never reached it at all. */
export class ServerUnavailableError extends Error {}

export async function login({ username, password }: LoginCredentials): Promise<void> {
  let response: Response;
  try {
    response = await apiFetch('/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password }),
    });
  } catch {
    throw new ServerUnavailableError('Unable to connect to the server. Please try again later.');
  }

  if (response.ok) {
    return;
  }

  if (response.status === 401) {
    // Server body is always the identical generic message; hardcode rather
    // than parse it so we never accidentally surface field-specific detail.
    throw new InvalidCredentialsError('Invalid username or password');
  }

  if (response.status === 429) {
    throw new TooManyAttemptsError('Too many failed login attempts. Please try again later.');
  }

  throw new ServerUnavailableError('Unable to connect to the server. Please try again later.');
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

/** Invalidates the session. Best-effort: a network failure still leaves the client logged-out. */
export async function logout(): Promise<void> {
  try {
    await apiFetch('/api/auth/logout', { method: 'POST' });
  } catch {
    // Ignored: callers treat logout as client-side-effective regardless.
  }
}

export interface RegisterInput {
  username: string;
  email: string;
  password: string;
  firstName: string;
}

/**
 * Registration was rejected (HTTP 400 weak password, or 409 username/email
 * already taken). Unlike login's generic message, the server distinguishes
 * these cases here -- there's no enumeration concern on the *write* path the
 * way there is on login/password-reset-request -- so the message is passed
 * through as-is.
 */
export class RegistrationError extends Error {}

export async function register({ username, email, password, firstName }: RegisterInput): Promise<void> {
  let response: Response;
  try {
    response = await apiFetch('/api/auth/register', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, email, password, firstName }),
    });
  } catch {
    throw new ServerUnavailableError('Unable to connect to the server. Please try again later.');
  }

  if (response.status === 201) {
    return;
  }

  if (response.status === 400 || response.status === 409) {
    const body = (await response.json()) as { message?: string };
    throw new RegistrationError(body.message ?? 'Registration failed.');
  }

  throw new ServerUnavailableError('Unable to connect to the server. Please try again later.');
}
