import { apiFetch } from './apiClient';
import { ApiError, MESSAGES, ServerUnavailableError, errorForStatus, readErrorBody, validationMessage } from './errors';

export { ServerUnavailableError } from './errors';

/**
 * A reset request/confirm was rejected (HTTP 400). The message is fixed:
 * `INVALID_TOKEN` maps to a client string; only a `VALIDATION_FAILED`
 * annotation message is passed through.
 */
export class PasswordResetError extends ApiError {}

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
    throw new ServerUnavailableError();
  }

  if (response.ok) {
    return;
  }

  if (response.status === 400) {
    const body = await readErrorBody(response);
    throw new PasswordResetError(validationMessage(body, MESSAGES.resetRequestInvalid));
  }

  throw errorForStatus(response.status);
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
    throw new ServerUnavailableError();
  }

  if (response.ok) {
    return;
  }

  if (response.status === 400) {
    const body = await readErrorBody(response);
    throw new PasswordResetError(
      body.code === 'INVALID_TOKEN'
        ? MESSAGES.resetInvalidToken
        : validationMessage(body, MESSAGES.resetInvalid),
    );
  }

  throw errorForStatus(response.status);
}
