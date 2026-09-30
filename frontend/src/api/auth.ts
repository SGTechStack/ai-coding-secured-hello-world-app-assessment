import { apiFetch, resetCsrfToken } from './client';
import type {
  HelloResponse,
  LoginInput,
  MessageResponse,
  RegisterInput,
  SessionUser,
  UserSummary,
} from './types';

export const authApi = {
  me: () => apiFetch<SessionUser>('/api/auth/me'),

  register: (input: RegisterInput) =>
    apiFetch<UserSummary>('/api/auth/register', { method: 'POST', body: input }),

  async login(input: LoginInput): Promise<SessionUser> {
    const session = await apiFetch<SessionUser>('/api/auth/login', { method: 'POST', body: input });
    // The server rotates the CSRF token together with the session id.
    resetCsrfToken();
    return session;
  },

  async logout(): Promise<void> {
    try {
      await apiFetch<void>('/api/auth/logout', { method: 'POST' });
    } finally {
      resetCsrfToken();
    }
  },

  requestPasswordReset: (email: string) =>
    apiFetch<MessageResponse>('/api/auth/password-reset/request', {
      method: 'POST',
      body: { email },
    }),

  confirmPasswordReset: (token: string, newPassword: string) =>
    apiFetch<MessageResponse>('/api/auth/password-reset/confirm', {
      method: 'POST',
      body: { token, newPassword },
    }),

  hello: () => apiFetch<HelloResponse>('/api/hello'),
};
