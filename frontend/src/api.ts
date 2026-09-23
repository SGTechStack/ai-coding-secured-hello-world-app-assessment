const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080';

export class ApiError extends Error {
  status: number;

  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

/** Reads the XSRF-TOKEN cookie that Spring Security's CookieCsrfTokenRepository sets. */
function readCsrfTokenFromCookie(): string | null {
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
  return match ? decodeURIComponent(match[1]) : null;
}

interface RequestOptions {
  method?: string;
  body?: unknown;
}

/**
 * Fetch wrapper for the backend API: always sends cookies (session +
 * CSRF), always echoes the CSRF token header on mutating requests, and
 * throws ApiError with the backend's error message on non-2xx responses.
 */
async function apiFetch<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const method = options.method ?? 'GET';
  const isMutating = method !== 'GET' && method !== 'HEAD';

  const headers: Record<string, string> = {};
  if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }
  if (isMutating) {
    const csrfToken = readCsrfTokenFromCookie();
    if (csrfToken) {
      headers['X-XSRF-TOKEN'] = csrfToken;
    }
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    method,
    headers,
    credentials: 'include',
    body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
  });

  if (!response.ok) {
    let message = `Request failed with status ${response.status}`;
    try {
      const errorBody = await response.json();
      if (errorBody && typeof errorBody.error === 'string') {
        message = errorBody.error;
      }
    } catch {
      // response had no JSON body; keep the generic message
    }
    throw new ApiError(response.status, message);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  const contentType = response.headers.get('Content-Type') ?? '';
  if (contentType.includes('application/json')) {
    return (await response.json()) as T;
  }
  return (await response.text()) as unknown as T;
}

/**
 * The backend only issues the CSRF cookie once a request has resolved
 * the deferred CsrfToken. Call this once on app load so the first
 * mutating request (e.g. registration) already has a token to send.
 */
export async function primeCsrfToken(): Promise<void> {
  await fetch(`${API_BASE_URL}/api/csrf`, { credentials: 'include' }).catch(() => {
    // Best-effort: if this fails, the first mutating request will still
    // work as long as the backend resolves the token on that request.
  });
}

export interface CurrentUserRole {
  role: 'USER' | 'ADMIN';
}

export const api = {
  register(payload: { username: string; email: string; password: string }) {
    return apiFetch<{ username: string }>('/api/register', { method: 'POST', body: payload });
  },

  login(payload: { username: string; password: string }) {
    return apiFetch<void>('/api/login', { method: 'POST', body: payload });
  },

  logout() {
    return apiFetch<void>('/api/logout', { method: 'POST' });
  },

  hello() {
    return apiFetch<string>('/api/hello');
  },

  requestPasswordReset(payload: { email: string }) {
    return apiFetch<{ message: string }>('/api/password-reset/request', { method: 'POST', body: payload });
  },

  confirmPasswordReset(payload: { token: string; newPassword: string }) {
    return apiFetch<{ message: string }>('/api/password-reset/confirm', { method: 'POST', body: payload });
  },

  adminListUsers() {
    return apiFetch<AdminUserView[]>('/api/admin/users');
  },

  adminUpdateStatus(id: number, enabled: boolean) {
    return apiFetch<void>(`/api/admin/users/${id}/status`, { method: 'PATCH', body: { enabled } });
  },

  adminUpdateRole(id: number, role: 'USER' | 'ADMIN') {
    return apiFetch<void>(`/api/admin/users/${id}/role`, { method: 'PATCH', body: { role } });
  },

  adminDeleteUser(id: number) {
    return apiFetch<void>(`/api/admin/users/${id}`, { method: 'DELETE' });
  },
};

export interface AdminUserView {
  id: number;
  username: string;
  email: string;
  role: 'USER' | 'ADMIN';
  enabled: boolean;
  createdAt: string;
}
