import { apiFetch } from './apiClient';
import type { Role } from './authApi';
import { ApiError, MESSAGES, ServerUnavailableError, errorForStatus, readErrorBody } from './errors';

export {
  ForbiddenError,
  NotFoundError,
  ServerUnavailableError,
  UnauthenticatedError,
} from './errors';

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
 * A request was rejected (HTTP 400: `SELF_ACTION`, `LAST_ADMIN`,
 * `VALIDATION_FAILED`). The panel disables the acting admin's own row, so the
 * self-action case is defense-in-depth. Messages are fixed client strings.
 */
export class AdminActionError extends ApiError {}

async function throwForResponse(response: Response): Promise<never> {
  if (response.status === 400) {
    const { code } = await readErrorBody(response);
    switch (code) {
      case 'SELF_ACTION':
        throw new AdminActionError(MESSAGES.adminSelfAction);
      case 'LAST_ADMIN':
        throw new AdminActionError(MESSAGES.adminLastAdmin);
      default:
        throw new AdminActionError(MESSAGES.adminActionFailed);
    }
  }

  throw errorForStatus(response.status);
}

async function request(path: string, options?: RequestInit): Promise<Response> {
  try {
    return await apiFetch(path, options);
  } catch {
    throw new ServerUnavailableError();
  }
}

export async function listUsers(): Promise<AdminUser[]> {
  const response = await request('/api/admin/users');
  if (!response.ok) {
    return throwForResponse(response);
  }
  return (await response.json()) as AdminUser[];
}

function patchJson(path: string, body: unknown): Promise<Response> {
  return request(path, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
}

export async function setEnabled(id: number, enabled: boolean): Promise<AdminUser> {
  const response = await patchJson(`/api/admin/users/${id}/status`, { enabled });
  if (!response.ok) {
    return throwForResponse(response);
  }
  return (await response.json()) as AdminUser;
}

export async function setRole(id: number, role: Role): Promise<AdminUser> {
  const response = await patchJson(`/api/admin/users/${id}/role`, { role });
  if (!response.ok) {
    return throwForResponse(response);
  }
  return (await response.json()) as AdminUser;
}

export async function deleteUser(id: number): Promise<void> {
  const response = await request(`/api/admin/users/${id}`, { method: 'DELETE' });
  if (response.status === 204) {
    return;
  }
  return throwForResponse(response);
}
