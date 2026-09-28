import { apiFetch } from './apiClient';

/** The server returned a 5xx, or the request never reached it at all. */
export class ServerUnavailableError extends Error {}

/**
 * The reset-confirm request was rejected (HTTP 400: expired/used/unknown
 * token, or a new password failing the strength policy). The server message
 * is passed through as-is -- there's no enumeration concern on this leg, only
 * on the request leg below.
 */
export class PasswordResetError extends Error {}

/**
 * Always resolves on a reachable server, whether or not the email matches a
 * registered account -- the backend's response is deliberately identical
 * either way (anti-enumeration, spec Story 6), so this client has nothing
 * account-specific to report back to the caller.
 */
export async function requestPasswordReset(email: string): Promise<void> {
  let response: Response;
  try {
    response = await apiFetch('/api/password-reset/request', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email }),
    });
  } catch {
    throw new ServerUnavailableError('Unable to connect to the server. Please try again later.');
  }

  if (!response.ok) {
    throw new ServerUnavailableError('Unable to connect to the server. Please try again later.');
  }
}

export async function confirmPasswordReset(token: string, newPassword: string): Promise<void> {
  let response: Response;
  try {
    response = await apiFetch('/api/password-reset/confirm', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ token, newPassword }),
    });
  } catch {
    throw new ServerUnavailableError('Unable to connect to the server. Please try again later.');
  }

  if (response.ok) {
    return;
  }

  if (response.status === 400) {
    const body = (await response.json()) as { message?: string };
    throw new PasswordResetError(body.message ?? 'Unable to reset your password.');
  }

  throw new ServerUnavailableError('Unable to connect to the server. Please try again later.');
}
