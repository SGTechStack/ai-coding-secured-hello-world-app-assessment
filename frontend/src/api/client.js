const API_BASE = import.meta.env?.VITE_API_BASE ?? "http://localhost:8080";

/**
 * Cross-origin fetch helper. `credentials: "include"` sends the session cookie
 * to the backend on its own origin; CORS on the backend allows this origin with
 * credentials.
 */
export async function apiFetch(path, options = {}) {
  const response = await fetch(`${API_BASE}${path}`, {
    credentials: "include",
    ...options,
  });
  return response;
}

export async function ping() {
  const response = await apiFetch("/api/ping");
  if (!response.ok) {
    throw new Error(`ping failed: ${response.status}`);
  }
  return response.json();
}

/** Reads the XSRF-TOKEN cookie the backend sets (CookieCsrfTokenRepository). */
function csrfToken() {
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
  return match ? decodeURIComponent(match[1]) : "";
}

export async function register({ username, email, password }) {
  // Prime the CSRF cookie, then submit with the token header.
  await apiFetch("/api/ping");
  const response = await apiFetch("/api/register", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "X-XSRF-TOKEN": csrfToken(),
    },
    body: JSON.stringify({ username, email, password }),
  });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new Error(data.message ?? `registration failed: ${response.status}`);
  }
  return data;
}

export async function login({ username, password }) {
  await apiFetch("/api/ping");
  const response = await apiFetch("/api/login", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "X-XSRF-TOKEN": csrfToken(),
    },
    body: JSON.stringify({ username, password }),
  });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    // Generic message — backend never reveals which field was wrong.
    throw new Error(data.message ?? "Invalid username or password");
  }
  return data;
}

/**
 * Requests a password reset for the given email. The backend always responds
 * with the same generic success message regardless of whether the email is
 * registered (enumeration resistance), so this returns that message on 200 and
 * only throws on an actual transport/server error.
 */
export async function requestPasswordReset({ email }) {
  await apiFetch("/api/ping");
  const response = await apiFetch("/api/password-reset/request", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "X-XSRF-TOKEN": csrfToken(),
    },
    body: JSON.stringify({ email }),
  });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new Error(data.message ?? `password reset request failed: ${response.status}`);
  }
  return data;
}

/**
 * Confirms a password reset: submits the reset token and the new password. The
 * backend validates the token (unknown/expired/used -> 400) and re-checks the
 * password strength policy, then updates the password and invalidates the user's
 * existing sessions. Follows the same CSRF-header pattern as the other
 * state-changing calls. Throws with the server message on any non-2xx response.
 */
export async function confirmPasswordReset({ token, newPassword }) {
  await apiFetch("/api/ping");
  const response = await apiFetch("/api/password-reset/confirm", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "X-XSRF-TOKEN": csrfToken(),
    },
    body: JSON.stringify({ token, newPassword }),
  });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new Error(data.message ?? `password reset failed: ${response.status}`);
  }
  return data;
}

/**
 * Fetches the admin user list (Story 8). Admin-only: the backend enforces the
 * `/api/admin/**` role guard server-side, so an authenticated non-admin gets 403
 * and an unauthenticated caller gets 401 — client state is never trusted for
 * authorization. Returns the array of users on success; throws otherwise.
 */
export async function fetchAdminUsers() {
  const response = await apiFetch("/api/admin/users");
  if (!response.ok) {
    throw new Error(`admin users fetch failed: ${response.status}`);
  }
  return response.json();
}

/**
 * Enables or disables a target account (Story 9). Admin-only PATCH under
 * `/api/admin/**`; the backend enforces the role guard server-side and rejects
 * an admin toggling their OWN account (400). State-changing, so it follows the
 * same CSRF-header pattern as the other write calls. Returns the updated user
 * projection on success; throws with the server message otherwise.
 */
export async function setUserEnabled(userId, enabled) {
  await apiFetch("/api/ping");
  const response = await apiFetch(`/api/admin/users/${userId}/enabled`, {
    method: "PATCH",
    headers: {
      "Content-Type": "application/json",
      "X-XSRF-TOKEN": csrfToken(),
    },
    body: JSON.stringify({ enabled }),
  });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new Error(data.message ?? `set enabled failed: ${response.status}`);
  }
  return data;
}

/**
 * Changes a target account's role (Story 10). Admin-only PATCH under
 * `/api/admin/**`; the backend enforces the role guard server-side, validates
 * the role value (only USER or ADMIN accepted -> otherwise 400), and rejects an
 * admin changing their OWN role (400 self-action guard). State-changing, so it
 * follows the same CSRF-header pattern as the other write calls. Returns the
 * updated user projection on success; throws with the server message otherwise.
 */
export async function changeUserRole(userId, role) {
  await apiFetch("/api/ping");
  const response = await apiFetch(`/api/admin/users/${userId}/role`, {
    method: "PATCH",
    headers: {
      "Content-Type": "application/json",
      "X-XSRF-TOKEN": csrfToken(),
    },
    body: JSON.stringify({ role }),
  });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new Error(data.message ?? `change role failed: ${response.status}`);
  }
  return data;
}

/**
 * Deletes a target account (Story 11). Admin-only DELETE under `/api/admin/**`;
 * the backend enforces the role guard server-side and rejects an admin deleting
 * their OWN account (400 self-action guard). State-changing, so it follows the
 * same CSRF-header pattern as the other write calls. Resolves on success (the
 * backend returns 204 No Content); throws with the server message otherwise.
 */
export async function deleteUser(userId) {
  await apiFetch("/api/ping");
  const response = await apiFetch(`/api/admin/users/${userId}`, {
    method: "DELETE",
    headers: {
      "X-XSRF-TOKEN": csrfToken(),
    },
  });
  if (!response.ok) {
    const data = await response.json().catch(() => ({}));
    throw new Error(data.message ?? `delete user failed: ${response.status}`);
  }
}

/** Fetches the protected greeting. Returns null when unauthenticated (401). */
export async function fetchGreeting() {
  const response = await apiFetch("/api/hello");
  if (response.status === 401) {
    return null;
  }
  if (!response.ok) {
    throw new Error(`greeting failed: ${response.status}`);
  }
  const data = await response.json();
  return data.message;
}

/**
 * Fetches the current authenticated user ({ username, role }). Returns null when
 * unauthenticated (401). Used to restore the session — including the role, which
 * drives admin navigation — after a page reload.
 */
export async function fetchMe() {
  const response = await apiFetch("/api/me");
  if (response.status === 401) {
    return null;
  }
  if (!response.ok) {
    throw new Error(`me failed: ${response.status}`);
  }
  return response.json();
}

/**
 * Ends the server-side session. Sends the CSRF token header like the other
 * state-changing calls; the backend invalidates the session and clears the
 * session cookie, so subsequent requests are unauthenticated.
 */
export async function logout() {
  const response = await apiFetch("/api/logout", {
    method: "POST",
    headers: {
      "X-XSRF-TOKEN": csrfToken(),
    },
  });
  if (!response.ok) {
    throw new Error(`logout failed: ${response.status}`);
  }
}
