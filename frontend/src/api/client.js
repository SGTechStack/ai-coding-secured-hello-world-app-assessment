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
