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
