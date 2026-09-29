import type { ProblemDetail } from './types';

const API_BASE = (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? '';

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly detail: string,
    public readonly raw: ProblemDetail | null = null,
  ) {
    super(detail);
    this.name = 'ApiError';
  }

  get isUnauthorized(): boolean { return this.status === 401; }
  get isForbidden(): boolean { return this.status === 403; }
  get isConflict(): boolean { return this.status === 409; }
  get isTooManyRequests(): boolean { return this.status === 429; }
}

/** Reads the XSRF-TOKEN cookie value set by the backend CSRF filter. */
export function getCsrfToken(): string | null {
  const match = `; ${document.cookie}`.match(/;\s*XSRF-TOKEN=([^;]+)/);
  return match ? decodeURIComponent(match[1]) : null;
}

/** Returns X-XSRF-TOKEN header for state-changing requests; empty for GET/HEAD/OPTIONS. */
function csrfHeaders(method: string): Record<string, string> {
  if (['GET', 'HEAD', 'OPTIONS', 'TRACE'].includes(method.toUpperCase())) {
    return {};
  }
  const token = getCsrfToken();
  return token ? { 'X-XSRF-TOKEN': token } : {};
}

async function parseError(response: Response): Promise<ApiError> {
  const contentType = response.headers.get('content-type') ?? '';
  if (contentType.includes('application/problem+json') || contentType.includes('application/json')) {
    try {
      const body = await response.json() as ProblemDetail;
      return new ApiError(response.status, body.detail ?? body.title ?? 'An error occurred', body);
    } catch {
      // fall through
    }
  }
  const fallbackMessages: Record<number, string> = {
    400: 'Invalid request',
    401: 'Invalid credentials or session expired',
    403: "You don't have permission to perform this action",
    404: 'Not found',
    409: 'Cannot perform this action',
    429: 'Too many requests — please try again later',
  };
  return new ApiError(
    response.status,
    fallbackMessages[response.status] ?? 'An unexpected error occurred',
  );
}

/**
 * Core fetch wrapper. Always includes credentials (session cookie) and the
 * CSRF header on state-changing methods. Never stores or logs passwords.
 */
export async function apiFetch(path: string, options: RequestInit = {}): Promise<Response> {
  const method = (options.method ?? 'GET').toUpperCase();
  const headers: Record<string, string> = {
    ...csrfHeaders(method),
  };
  if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }

  const response = await fetch(`${API_BASE}${path}`, {
    ...options,
    credentials: 'include',
    headers: { ...headers, ...(options.headers as Record<string, string> | undefined) },
  });

  if (!response.ok) {
    throw await parseError(response);
  }
  return response;
}

export async function apiJson<T>(path: string, options: RequestInit = {}): Promise<T> {
  const response = await apiFetch(path, options);
  if (response.status === 204) {
    return undefined as unknown as T;
  }
  return response.json() as Promise<T>;
}
