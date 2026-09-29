import { apiFetch, apiJson } from './client';
import type { AuthResponse, UserResponse } from './types';

/**
 * Calls GET /api/auth/csrf to seed the XSRF-TOKEN cookie before any
 * state-changing request. Must be called once on application startup.
 * The actual cookie is set by the Spring Security CSRF filter in the response.
 */
export async function bootstrapCsrf(): Promise<void> {
  await apiFetch('/api/auth/csrf');
}

export async function getMe(): Promise<UserResponse> {
  return apiJson<UserResponse>('/api/auth/me');
}

export async function login(username: string, password: string): Promise<AuthResponse> {
  return apiJson<AuthResponse>('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username, password }),
  });
}

export async function logout(): Promise<void> {
  await apiFetch('/api/auth/logout', { method: 'POST' });
}

export async function register(
  username: string,
  email: string,
  password: string,
): Promise<UserResponse> {
  return apiJson<UserResponse>('/api/auth/register', {
    method: 'POST',
    body: JSON.stringify({ username, email, password }),
  });
}

export async function requestPasswordReset(email: string): Promise<string> {
  const data = await apiJson<{ message: string }>('/api/auth/password-reset/request', {
    method: 'POST',
    body: JSON.stringify({ email }),
  });
  return data.message;
}

export async function confirmPasswordReset(token: string, newPassword: string): Promise<string> {
  const data = await apiJson<{ message: string }>('/api/auth/password-reset/confirm', {
    method: 'POST',
    body: JSON.stringify({ token, newPassword }),
  });
  return data.message;
}
