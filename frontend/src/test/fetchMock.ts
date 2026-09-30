import { type Mock, vi } from 'vitest';

export const TEST_CSRF_TOKEN = 'test-csrf-token';

export function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

export function csrfResponse(token: string = TEST_CSRF_TOKEN): Response {
  return jsonResponse(200, { headerName: 'X-CSRF-TOKEN', token });
}

export type FetchMock = Mock<typeof fetch>;

/**
 * Stubs global `fetch` so `GET /api/auth/csrf` is answered automatically
 * (the CSRF bootstrap every state-changing request triggers), and every other
 * request is delegated to the returned mock -- which tests program and assert
 * on exactly as they would a bare `fetch` mock.
 */
export function stubFetchWithCsrf(): FetchMock {
  const api: FetchMock = vi.fn<typeof fetch>();
  vi.stubGlobal(
    'fetch',
    vi.fn<typeof fetch>((input, init) => {
      const url = typeof input === 'string' ? input : input.toString();
      if (url.endsWith('/api/auth/csrf')) {
        return Promise.resolve(csrfResponse());
      }
      return api(input, init);
    }),
  );
  return api;
}
