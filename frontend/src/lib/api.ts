export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
    public fields: Record<string, string> = {},
  ) {
    super(message);
  }
}

const baseUrl = import.meta.env.VITE_API_URL || 'http://localhost:8080/api';

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  let response: Response;
  try {
    response = await fetch(`${baseUrl}${path}`, { ...options, credentials: 'include' });
  } catch {
    throw new ApiError(0, 'Unable to connect. Check your connection and try again.');
  }
  if (response.status === 204) return undefined as T;
  const data = (await response.json().catch(() => null)) as {
    message?: string;
    errors?: Record<string, string>;
  } | null;
  if (!response.ok) {
    if (response.status === 401 && path !== '/auth/login') {
      window.dispatchEvent(new Event('session-expired'));
    }
    throw new ApiError(
      response.status,
      response.status >= 500
        ? 'Something went wrong. Please try again.'
        : data?.message || 'Request failed. Please try again.',
      data?.errors,
    );
  }
  return data as T;
}

async function mutate<T>(method: string, path: string, body?: unknown): Promise<T> {
  const csrf = await request<{ token: string; headerName: string }>('/auth/csrf');
  return request<T>(path, {
    method,
    headers: { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
}

export const api = {
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, body?: unknown) => mutate<T>('POST', path, body),
  patch: <T>(path: string, body: unknown) => mutate<T>('PATCH', path, body),
  delete: (path: string) => mutate<void>('DELETE', path),
};
