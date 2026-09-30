const API_URL = import.meta.env.VITE_API_URL ?? 'http://localhost:8080';

export class ApiError extends Error {
  constructor(status, message, fields) {
    super(message);
    this.status = status;
    this.fields = fields;
  }
}

// The CSRF token is kept in memory only. The backend rotates it on login, so we drop it then.
let csrfToken = null;

async function getCsrfToken() {
  if (!csrfToken) {
    const res = await fetch(`${API_URL}/api/csrf`, { credentials: 'include' });
    csrfToken = (await res.json()).token;
  }
  return csrfToken;
}

async function request(method, path, body, retried = false) {
  const headers = {};
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (method !== 'GET') headers['X-XSRF-TOKEN'] = await getCsrfToken();

  const res = await fetch(`${API_URL}${path}`, {
    method,
    headers,
    credentials: 'include',
    body: body === undefined ? undefined : JSON.stringify(body),
  });

  // A stale CSRF token (e.g. server restart) yields 403: refresh once and retry.
  if (res.status === 403 && method !== 'GET' && !retried) {
    csrfToken = null;
    return request(method, path, body, true);
  }

  const isJson = res.headers.get('content-type')?.includes('application/json');
  const data = isJson ? await res.json() : await res.text();
  if (!res.ok) {
    throw new ApiError(res.status, data?.error ?? res.statusText ?? 'Request failed', data?.fields);
  }
  return data;
}

export const api = {
  register: (username, email, password) => request('POST', '/api/auth/register', { username, email, password }),
  login: async (username, password) => {
    const me = await request('POST', '/api/auth/login', { username, password });
    csrfToken = null;
    return me;
  },
  logout: async () => {
    await request('POST', '/api/auth/logout');
    csrfToken = null;
  },
  me: () => request('GET', '/api/auth/me'),
  hello: () => request('GET', '/api/hello'),
  requestReset: (email) => request('POST', '/api/auth/password-reset/request', { email }),
  confirmReset: (token, newPassword) =>
    request('POST', '/api/auth/password-reset/confirm', { token, newPassword }),
  admin: {
    list: () => request('GET', '/api/admin/users'),
    setEnabled: (id, enabled) => request('PATCH', `/api/admin/users/${id}/enabled`, { enabled }),
    setRole: (id, role) => request('PATCH', `/api/admin/users/${id}/role`, { role }),
    remove: (id) => request('DELETE', `/api/admin/users/${id}`),
  },
};
