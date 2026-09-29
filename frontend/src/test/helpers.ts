import type { UserResponse } from '../api/types';

export const mockUser = (overrides?: Partial<UserResponse>): UserResponse => ({
  id: '550e8400-e29b-41d4-a716-446655440000',
  username: 'alice',
  email: 'alice@example.com',
  role: 'USER',
  enabled: true,
  createdAt: '2026-01-01T00:00:00Z',
  ...overrides,
});

export const mockAdmin = (overrides?: Partial<UserResponse>): UserResponse =>
  mockUser({ id: '550e8400-e29b-41d4-a716-446655440001', username: 'admin', email: 'admin@example.com', role: 'ADMIN', ...overrides });

/** Sets the XSRF-TOKEN cookie as the browser would after bootstrapping CSRF. */
export function setCsrfCookie(token = 'test-csrf-token') {
  document.cookie = `XSRF-TOKEN=${token}; path=/`;
}

/** Creates a minimal mock Response. */
export function mockResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

export function mockProblemDetail(status: number, detail: string): Response {
  return new Response(JSON.stringify({ status, detail }), {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  });
}
