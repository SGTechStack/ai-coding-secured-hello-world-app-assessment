/**
 * Shared `fetch` wrapper for every backend call: the frontend and backend
 * run on separate origins (see `VITE_API_BASE_URL` and the backend's
 * `app.cors.allowed-origins`), so every request needs the base URL prepended,
 * `credentials: 'include'` to carry the session cookie cross-origin, and the
 * CSRF header attached for anything that isn't a plain read.
 *
 * CSRF (spec D1): the token lives in the server-side session and is fetched
 * lazily from `GET /api/auth/csrf`, which answers `{headerName, token}`. It is
 * cached **in memory only** (never cookies / localStorage / sessionStorage)
 * and sent as `headerName` (`X-CSRF-TOKEN`) on every non-GET/HEAD request.
 * The server rotates the token on login and destroys it on logout, so callers
 * invoke `clearCsrfToken()` after either. A 403 on a state-changing request
 * (e.g. the session expired and took its token with it) clears the cache,
 * refetches, and retries the request exactly once.
 *
 * Reads `import.meta.env.VITE_API_BASE_URL` on every call (rather than once
 * at module load) so tests can stub it per-case with `vi.stubEnv` without
 * needing a module reset.
 */

interface CsrfToken {
  headerName: string;
  token: string;
}

const SAFE_METHODS = new Set(['GET', 'HEAD']);

let csrfTokenPromise: Promise<CsrfToken> | null = null;

function apiUrl(path: string): string {
  const baseUrl = import.meta.env.VITE_API_BASE_URL ?? '';
  return `${baseUrl}${path}`;
}

async function fetchCsrfToken(): Promise<CsrfToken> {
  const response = await fetch(apiUrl('/api/auth/csrf'), {
    method: 'GET',
    credentials: 'include',
    cache: 'no-store',
  });
  if (!response.ok) {
    throw new Error(`CSRF token request failed with status ${response.status}`);
  }
  const body = (await response.json()) as Partial<CsrfToken> | null;
  if (!body || typeof body.headerName !== 'string' || typeof body.token !== 'string' || !body.headerName) {
    throw new Error('Malformed CSRF token response');
  }
  return { headerName: body.headerName, token: body.token };
}

/** Returns the cached token, fetching it once if absent. Concurrent callers share one request. */
function getCsrfToken(): Promise<CsrfToken> {
  if (!csrfTokenPromise) {
    const pending = fetchCsrfToken();
    csrfTokenPromise = pending;
    // Don't cache a failure: the next request should try again.
    pending.catch(() => {
      if (csrfTokenPromise === pending) {
        csrfTokenPromise = null;
      }
    });
  }
  return csrfTokenPromise;
}

/** Drops the cached CSRF token; the next state-changing request fetches a fresh one. */
export function clearCsrfToken(): void {
  csrfTokenPromise = null;
}

function send(path: string, options: RequestInit, method: string, csrf: CsrfToken | null): Promise<Response> {
  const headers = new Headers(options.headers);
  if (csrf) {
    headers.set(csrf.headerName, csrf.token);
  }
  return fetch(apiUrl(path), {
    ...options,
    method,
    credentials: 'include',
    headers,
  });
}

/**
 * Rejects (like `fetch`) on a network failure, including a failure to obtain
 * the CSRF token; otherwise resolves with the response, whatever its status.
 *
 * Note: request bodies must be replayable (strings, as every caller uses) for
 * the single 403 retry.
 */
export async function apiFetch(path: string, options: RequestInit = {}): Promise<Response> {
  const method = (options.method ?? 'GET').toUpperCase();

  if (SAFE_METHODS.has(method)) {
    return send(path, options, method, null);
  }

  const response = await send(path, options, method, await getCsrfToken());
  if (response.status !== 403) {
    return response;
  }

  // Possibly a stale/missing token (CSRF_INVALID): refresh and retry once.
  // A second 403 is returned to the caller as a genuine permission failure.
  clearCsrfToken();
  return send(path, options, method, await getCsrfToken());
}
