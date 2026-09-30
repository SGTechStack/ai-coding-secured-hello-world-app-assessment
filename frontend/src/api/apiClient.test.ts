import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { csrfResponse, jsonResponse } from '../test/fetchMock';
import { apiFetch, clearCsrfToken } from './apiClient';

function callsTo(path: string) {
  return vi.mocked(fetch).mock.calls.filter(([input]) => String(input).endsWith(path));
}

function csrfHeaderOf(call: Parameters<typeof fetch>) {
  return new Headers(call[1]?.headers).get('X-CSRF-TOKEN');
}

describe('apiFetch', () => {
  beforeEach(() => {
    vi.stubGlobal(
      'fetch',
      vi.fn<typeof fetch>((input) =>
        Promise.resolve(
          String(input).endsWith('/api/auth/csrf') ? csrfResponse('token-1') : new Response(null, { status: 200 }),
        ),
      ),
    );
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.unstubAllEnvs();
  });

  it('prepends VITE_API_BASE_URL to the request path', async () => {
    vi.stubEnv('VITE_API_BASE_URL', 'http://localhost:8080');

    await apiFetch('/api/hello');

    expect(fetch).toHaveBeenCalledWith('http://localhost:8080/api/hello', expect.anything());
  });

  it('falls back to a relative path when no base URL is configured', async () => {
    vi.stubEnv('VITE_API_BASE_URL', '');

    await apiFetch('/api/hello');

    expect(fetch).toHaveBeenCalledWith('/api/hello', expect.anything());
  });

  it('always sends credentials: include', async () => {
    await apiFetch('/api/hello');

    expect(fetch).toHaveBeenCalledWith(expect.any(String), expect.objectContaining({ credentials: 'include' }));
  });

  it('defaults to GET when no method is specified', async () => {
    await apiFetch('/api/hello');

    expect(fetch).toHaveBeenCalledWith(expect.any(String), expect.objectContaining({ method: 'GET' }));
  });

  it('does not fetch a CSRF token or attach the header for a GET request', async () => {
    await apiFetch('/api/hello');

    expect(fetch).toHaveBeenCalledTimes(1);
    expect(csrfHeaderOf(vi.mocked(fetch).mock.calls[0])).toBeNull();
  });

  it('lazily fetches the CSRF token (credentials: include, base URL) before the first non-GET request', async () => {
    vi.stubEnv('VITE_API_BASE_URL', 'http://localhost:8080');

    await apiFetch('/api/auth/logout', { method: 'POST' });

    const [first, second] = vi.mocked(fetch).mock.calls;
    expect(first[0]).toBe('http://localhost:8080/api/auth/csrf');
    expect(first[1]).toEqual(expect.objectContaining({ method: 'GET', credentials: 'include' }));
    expect(second[0]).toBe('http://localhost:8080/api/auth/logout');
    expect(csrfHeaderOf(second)).toBe('token-1');
  });

  it('sends the token under the header name the server returned', async () => {
    vi.mocked(fetch).mockImplementation((input) =>
      Promise.resolve(
        String(input).endsWith('/api/auth/csrf')
          ? jsonResponse(200, { headerName: 'X-Custom-Csrf', token: 'custom' })
          : new Response(null, { status: 200 }),
      ),
    );

    await apiFetch('/api/auth/login', { method: 'POST' });

    const [, call] = vi.mocked(fetch).mock.calls;
    expect(new Headers(call[1]?.headers).get('X-Custom-Csrf')).toBe('custom');
  });

  it('caches the token in memory across requests', async () => {
    await apiFetch('/api/auth/login', { method: 'POST' });
    await apiFetch('/api/admin/users/2', { method: 'DELETE' });

    expect(callsTo('/api/auth/csrf')).toHaveLength(1);
    expect(csrfHeaderOf(callsTo('/api/admin/users/2')[0])).toBe('token-1');
  });

  it('refetches the token after clearCsrfToken()', async () => {
    await apiFetch('/api/auth/login', { method: 'POST' });
    clearCsrfToken();
    await apiFetch('/api/auth/logout', { method: 'POST' });

    expect(callsTo('/api/auth/csrf')).toHaveLength(2);
  });

  it('never writes the token to web storage or cookies', async () => {
    await apiFetch('/api/auth/login', { method: 'POST' });

    expect(window.localStorage.length).toBe(0);
    expect(window.sessionStorage.length).toBe(0);
    expect(document.cookie).not.toContain('token-1');
  });

  it('preserves caller-supplied headers alongside the CSRF header', async () => {
    await apiFetch('/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
    });

    const headers = new Headers(callsTo('/api/auth/login')[0][1]?.headers);
    expect(headers.get('Content-Type')).toBe('application/json');
    expect(headers.get('X-CSRF-TOKEN')).toBe('token-1');
  });

  it('on a 403, refetches the token and retries the request once with the new token', async () => {
    let csrfCalls = 0;
    let loginCalls = 0;
    vi.mocked(fetch).mockImplementation((input) => {
      if (String(input).endsWith('/api/auth/csrf')) {
        csrfCalls += 1;
        return Promise.resolve(csrfResponse(`token-${csrfCalls}`));
      }
      loginCalls += 1;
      return Promise.resolve(
        loginCalls === 1 ? jsonResponse(403, { code: 'CSRF_INVALID' }) : new Response(null, { status: 200 }),
      );
    });

    const response = await apiFetch('/api/auth/login', { method: 'POST', body: '{"a":1}' });

    expect(response.status).toBe(200);
    const loginRequests = callsTo('/api/auth/login');
    expect(loginRequests).toHaveLength(2);
    expect(csrfHeaderOf(loginRequests[0])).toBe('token-1');
    expect(csrfHeaderOf(loginRequests[1])).toBe('token-2');
    expect(loginRequests[1][1]?.body).toBe('{"a":1}');
  });

  it('retries at most once: a second 403 is returned to the caller', async () => {
    vi.mocked(fetch).mockImplementation((input) =>
      Promise.resolve(
        String(input).endsWith('/api/auth/csrf') ? csrfResponse() : jsonResponse(403, { code: 'FORBIDDEN' }),
      ),
    );

    const response = await apiFetch('/api/admin/users/2', { method: 'DELETE' });

    expect(response.status).toBe(403);
    expect(callsTo('/api/admin/users/2')).toHaveLength(2);
    expect(callsTo('/api/auth/csrf')).toHaveLength(2);
  });

  it('does not retry a 403 on a GET request', async () => {
    vi.mocked(fetch).mockResolvedValue(jsonResponse(403, { code: 'FORBIDDEN' }));

    const response = await apiFetch('/api/admin/users');

    expect(response.status).toBe(403);
    expect(fetch).toHaveBeenCalledTimes(1);
  });

  it('rejects (like a network failure) when the CSRF token cannot be fetched, and does not cache the failure', async () => {
    vi.mocked(fetch).mockResolvedValueOnce(jsonResponse(503, {}));

    await expect(apiFetch('/api/auth/login', { method: 'POST' })).rejects.toThrow();
    expect(callsTo('/api/auth/login')).toHaveLength(0);

    await apiFetch('/api/auth/login', { method: 'POST' });
    expect(callsTo('/api/auth/login')).toHaveLength(1);
  });
});
