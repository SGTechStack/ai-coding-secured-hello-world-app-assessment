import { apiFetch } from './apiClient';
import type { Role } from './authApi';

/** A row in the admin user table, as returned by `GET /api/admin/users`. */
export interface AdminUser {
  id: number;
  username: string;
  email: string;
  role: Role;
  enabled: boolean;
  createdAt: string;
}

/**
 * A request was rejected (HTTP 400 self-action guard, e.g. "Cannot change
 * your own role"). The route is gated behind `RequireAdmin`, and the panel
 * itself disables the acting admin's own row, so this is defense-in-depth
 * rather than a path a user can normally reach.
 */
export class AdminActionError extends Error {}

/** The server returned a 5xx, or the request never reached it at all. */
export class ServerUnavailableError extends Error {}

async function parseErrorOrThrowUnavailable(response: Response): Promise<never> {
  if (response.status === 400) {
    const body = (await response.json()) as { message?: string };
    throw new AdminActionError(body.message ?? 'That action could not be completed.');
  }

  throw new ServerUnavailableError('Unable to connect to the server. Please try again later.');
}

export async function listUsers(): Promise<AdminUser[]> {
  let response: Response;
  try {
    response = await apiFetch('/api/admin/users');
  } catch {
    throw new ServerUnavailableError('Unable to connect to the server. Please try again later.');
  }

  if (!response.ok) {
    return parseErrorOrThrowUnavailable(response);
  }

  return (await response.json()) as AdminUser[];
}

export async function setEnabled(id: number, enabled: boolean): Promise<AdminUser> {
  let response: Response;
  try {
    response = await apiFetch(`/api/admin/users/${id}/status`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ enabled }),
    });
  } catch {
    throw new ServerUnavailableError('Unable to connect to the server. Please try again later.');
  }

  if (!response.ok) {
    return parseErrorOrThrowUnavailable(response);
  }

  return (await response.json()) as AdminUser;
}

export async function setRole(id: number, role: Role): Promise<AdminUser> {
  let response: Response;
  try {
    response = await apiFetch(`/api/admin/users/${id}/role`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ role }),
    });
  } catch {
    throw new ServerUnavailableError('Unable to connect to the server. Please try again later.');
  }

  if (!response.ok) {
    return parseErrorOrThrowUnavailable(response);
  }

  return (await response.json()) as AdminUser;
}

export async function deleteUser(id: number): Promise<void> {
  let response: Response;
  try {
    response = await apiFetch(`/api/admin/users/${id}`, { method: 'DELETE' });
  } catch {
    throw new ServerUnavailableError('Unable to connect to the server. Please try again later.');
  }

  if (response.status === 204) {
    return;
  }

  return parseErrorOrThrowUnavailable(response);
}
