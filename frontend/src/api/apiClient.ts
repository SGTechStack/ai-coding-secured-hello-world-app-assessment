import { readCsrfToken } from './csrf';

/**
 * Shared `fetch` wrapper for every backend call: the frontend and backend now
 * run on separate origins (see `VITE_API_BASE_URL` and the backend's
 * `app.cors.allowed-origins`), so every request needs the base URL prepended,
 * `credentials: 'include'` to carry the session cookie cross-origin, and the
 * CSRF header attached for anything that isn't a plain read.
 *
 * Reads `import.meta.env.VITE_API_BASE_URL` on every call (rather than once
 * at module load) so tests can stub it per-case with `vi.stubEnv` without
 * needing a module reset.
 */
export async function apiFetch(path: string, options: RequestInit = {}): Promise<Response> {
  const baseUrl = import.meta.env.VITE_API_BASE_URL ?? '';
  const method = (options.method ?? 'GET').toUpperCase();
  const headers = new Headers(options.headers);

  if (method !== 'GET') {
    const csrfToken = readCsrfToken();
    if (csrfToken) {
      headers.set('X-XSRF-TOKEN', csrfToken);
    }
  }

  return fetch(`${baseUrl}${path}`, {
    ...options,
    method,
    credentials: 'include',
    headers,
  });
}
