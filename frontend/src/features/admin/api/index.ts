import { API_BASE_URL, CONFIRM_PASSWORD_HEADER, csrfHeader, parseErrorResponse } from "@/common/api/http";
import type { UserRole } from "@/features/auth/api";

/**
 * Note `maskedEmail`, not `email`. The listing endpoint no longer returns full
 * addresses: one request used to hand over every registered address in the
 * system, which is the entire personal-data holding, to any admin, as the
 * default payload of the user-management screen.
 *
 * The masked form (`s****@example.com`) keeps the one legitimate use — telling
 * two similarly-named accounts apart before acting irreversibly on the wrong
 * one. The real address comes from {@link fetchUserEmail}, one account at a
 * time, with a stated purpose.
 */
export interface AdminUserSummary {
  id: string;
  username: string;
  maskedEmail: string;
  role: UserRole;
  enabled: boolean;
  /**
   * Whether the automatic login-failure lockout is currently in effect.
   * Independent of `enabled` — an account can be enabled and locked at the
   * same time, since disabling/enabling never touches the lockout counter.
   */
  locked: boolean;
  createdAt: string;
  /** Count of successful logins. Informational only. */
  loginCount: number;
  /** ISO timestamp of the last successful login, or null if never. */
  lastLoginAt: string | null;
}

export interface UserEmailResponse {
  id: string;
  username: string;
  email: string;
  purpose: string;
}

/**
 * Lists all registered users. Admin-only: throws {@link ApiError} with a
 * 403-flavoured message for a non-admin session, 401 for no session at
 * all — the role check happens entirely server-side, this call just
 * surfaces whatever the backend decides.
 */
export async function fetchAdminUsers(signal?: AbortSignal): Promise<AdminUserSummary[]> {
  const response = await fetch(`${API_BASE_URL}/api/admin/users`, {
    method: "GET",
    credentials: "include",
    signal,
  });

  if (!response.ok) {
    return parseErrorResponse(response);
  }

  return response.json() as Promise<AdminUserSummary[]>;
}

/**
 * Enables or disables another user's account. Throws {@link ApiError}
 * (400) if the target is the caller's own account — the backend rejects
 * self-targeting outright, this call just surfaces that.
 */
export async function setUserEnabled(userId: string, enabled: boolean): Promise<AdminUserSummary> {
  const response = await fetch(`${API_BASE_URL}/api/admin/users/${userId}/enabled`, {
    method: "PATCH",
    credentials: "include",
    headers: { "Content-Type": "application/json", ...(await csrfHeader()) },
    body: JSON.stringify({ enabled }),
  });

  if (!response.ok) {
    return parseErrorResponse(response);
  }

  return response.json() as Promise<AdminUserSummary>;
}

/**
 * Clears an account's lockout state ahead of its automatic cooldown.
 * Separate from {@link setUserEnabled}: enabling a disabled account does not
 * touch the lockout counter, and this is the only call that does.
 */
export async function unlockUser(userId: string): Promise<AdminUserSummary> {
  const response = await fetch(`${API_BASE_URL}/api/admin/users/${userId}/unlock`, {
    method: "POST",
    credentials: "include",
    headers: { ...(await csrfHeader()) },
  });

  if (!response.ok) {
    return parseErrorResponse(response);
  }

  return response.json() as Promise<AdminUserSummary>;
}

/**
 * Changes another user's role. Throws {@link ApiError} (400) if the
 * target is the caller's own account.
 */
export async function setUserRole(userId: string, role: UserRole): Promise<AdminUserSummary> {
  const response = await fetch(`${API_BASE_URL}/api/admin/users/${userId}/role`, {
    method: "PATCH",
    credentials: "include",
    headers: { "Content-Type": "application/json", ...(await csrfHeader()) },
    body: JSON.stringify({ role }),
  });

  if (!response.ok) {
    return parseErrorResponse(response);
  }

  return response.json() as Promise<AdminUserSummary>;
}

/**
 * Reveals one user's full email address, for a stated purpose.
 *
 * The purpose is required by the server and recorded in an audit row. Passing a
 * placeholder would satisfy the type and defeat the point, so the UI asks the
 * operator for it rather than inventing one.
 *
 * Throws {@link ApiError} (400) if the purpose is blank.
 */
export async function fetchUserEmail(userId: string, purpose: string): Promise<UserEmailResponse> {
  const query = new URLSearchParams({ purpose });

  const response = await fetch(`${API_BASE_URL}/api/admin/users/${userId}/email?${query}`, {
    method: "GET",
    credentials: "include",
  });

  if (!response.ok) {
    return parseErrorResponse(response);
  }

  return response.json() as Promise<UserEmailResponse>;
}

/**
 * Deletes another user's account.
 *
 * Requires the acting admin's current password, sent in a header: a session
 * proves somebody authenticated hours ago, not that this request came from them,
 * and this action cannot be undone.
 *
 * Throws {@link ApiError} for a self-target (400), a missing or wrong
 * confirmation (403), or an attempt to remove the last enabled admin (409).
 */
export async function deleteUser(userId: string, confirmationPassword: string): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/api/admin/users/${userId}`, {
    method: "DELETE",
    credentials: "include",
    headers: {
      ...(await csrfHeader()),
      [CONFIRM_PASSWORD_HEADER]: confirmationPassword,
    },
  });

  if (!response.ok) {
    return parseErrorResponse(response);
  }
}
