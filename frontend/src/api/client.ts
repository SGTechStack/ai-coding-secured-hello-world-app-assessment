// API client for the Spring Boot backend. The backend lives on its own origin
// (default http://localhost:8080), so every call is cross-origin. `credentials:
// 'include'` sends/receives the session cookie; the backend CORS allow-list
// permits this origin with credentials.
//
// CSRF: the backend issues a JS-readable `XSRF-TOKEN` cookie (CookieCsrfTokenRepository
// .withHttpOnlyFalse()) that must be echoed as the `X-XSRF-TOKEN` header on every
// mutating request except /api/register and /api/password-reset/request, which the
// backend explicitly exempts.
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080';

export class ApiError extends Error {
  status: number;

  constructor(status: number, message: string) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }
}

export interface HealthResponse {
  status: string;
  time: string;
}

export type Role = 'USER' | 'ADMIN';

export interface UserSummary {
  id: number;
  username: string;
  email: string;
  role: Role;
  enabled: boolean;
  createdAt: string;
}

export interface RegistrationRequest {
  username: string;
  email: string;
  password: string;
}

export type RegistrationResponse = UserSummary;

export interface LoginResponse {
  username: string;
  role: Role;
}

export interface MessageResponse {
  message: string;
}

function readCsrfCookie(): string | null {
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
  return match ? decodeURIComponent(match[1]) : null;
}

async function ensureCsrfCookie(): Promise<void> {
  if (readCsrfCookie()) return;
  await fetch(`${API_BASE_URL}/api/csrf`, {
    method: 'GET',
    credentials: 'include',
  });
}

async function extractErrorMessage(response: Response): Promise<string> {
  try {
    const text = await response.text();
    if (text) {
      const body = JSON.parse(text) as { error?: string; message?: string };
      if (body.error) return body.error;
      if (body.message) return body.message;
    }
  } catch {
    // Non-JSON or empty error body — fall through to the generic message below.
  }
  return `Request failed: HTTP ${response.status}`;
}

interface RequestOptions {
  method?: string;
  body?: unknown;
  skipCsrf?: boolean;
  parse?: 'json' | 'text' | 'none';
}

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, skipCsrf = false, parse = 'json' } = options;
  const headers: Record<string, string> = {};
  let requestBody: string | undefined;

  if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
    requestBody = JSON.stringify(body);
  }

  if (method !== 'GET' && !skipCsrf) {
    await ensureCsrfCookie();
    const token = readCsrfCookie();
    if (token) {
      headers['X-XSRF-TOKEN'] = token;
    }
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    method,
    headers,
    body: requestBody,
    credentials: 'include',
  });

  if (!response.ok) {
    throw new ApiError(response.status, await extractErrorMessage(response));
  }

  if (parse === 'none') {
    return undefined as T;
  }
  if (parse === 'text') {
    return (await response.text()) as unknown as T;
  }
  const text = await response.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

export async function fetchHealth(): Promise<HealthResponse> {
  return request<HealthResponse>('/api/health');
}

export async function register(data: RegistrationRequest): Promise<RegistrationResponse> {
  return request<RegistrationResponse>('/api/register', { method: 'POST', body: data, skipCsrf: true });
}

export async function login(username: string, password: string): Promise<LoginResponse> {
  return request<LoginResponse>('/api/login', { method: 'POST', body: { username, password } });
}

export async function logout(): Promise<void> {
  return request<void>('/api/logout', { method: 'POST', parse: 'none' });
}

const GREETING_PATTERN = /^Hello, (.+)$/;

export function parseGreeting(greeting: string): string | null {
  const match = greeting.match(GREETING_PATTERN);
  return match ? match[1] : null;
}

export async function fetchHello(): Promise<string> {
  return request<string>('/api/hello', { parse: 'text' });
}

export async function requestPasswordReset(email: string): Promise<MessageResponse> {
  return request<MessageResponse>('/api/password-reset/request', {
    method: 'POST',
    body: { email },
    skipCsrf: true,
  });
}

export async function confirmPasswordReset(token: string, newPassword: string): Promise<MessageResponse> {
  return request<MessageResponse>('/api/password-reset/confirm', {
    method: 'POST',
    body: { token, newPassword },
  });
}

export async function adminListUsers(): Promise<UserSummary[]> {
  return request<UserSummary[]>('/api/admin/users');
}

export async function adminSetEnabled(id: number, enabled: boolean): Promise<void> {
  return request<void>(`/api/admin/users/${id}/enabled`, {
    method: 'PATCH',
    body: { enabled },
    parse: 'none',
  });
}

export async function adminSetRole(id: number, role: Role): Promise<void> {
  return request<void>(`/api/admin/users/${id}/role`, {
    method: 'PATCH',
    body: { role },
    parse: 'none',
  });
}

export async function adminDeleteUser(id: number): Promise<void> {
  return request<void>(`/api/admin/users/${id}`, { method: 'DELETE', parse: 'none' });
}
