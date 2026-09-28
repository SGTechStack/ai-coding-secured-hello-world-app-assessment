import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { apiFetch } from './apiClient';

function clearCsrfCookie() {
  document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 UTC; path=/;';
}

describe('apiFetch', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 200 })));
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.unstubAllEnvs();
    clearCsrfCookie();
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

  it('attaches X-XSRF-TOKEN for a non-GET request when the cookie is present', async () => {
    document.cookie = 'XSRF-TOKEN=test-token-value';

    await apiFetch('/api/auth/logout', { method: 'POST' });

    const [, options] = vi.mocked(fetch).mock.calls[0];
    expect(new Headers(options?.headers).get('X-XSRF-TOKEN')).toBe('test-token-value');
  });

  it('does not attach X-XSRF-TOKEN for a GET request even when the cookie is present', async () => {
    document.cookie = 'XSRF-TOKEN=test-token-value';

    await apiFetch('/api/hello');

    const [, options] = vi.mocked(fetch).mock.calls[0];
    expect(new Headers(options?.headers).get('X-XSRF-TOKEN')).toBeNull();
  });

  it('omits X-XSRF-TOKEN for a non-GET request when there is no cookie', async () => {
    await apiFetch('/api/auth/logout', { method: 'POST' });

    const [, options] = vi.mocked(fetch).mock.calls[0];
    expect(new Headers(options?.headers).get('X-XSRF-TOKEN')).toBeNull();
  });

  it('preserves caller-supplied headers alongside the CSRF header', async () => {
    document.cookie = 'XSRF-TOKEN=test-token-value';

    await apiFetch('/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
    });

    const [, options] = vi.mocked(fetch).mock.calls[0];
    const headers = new Headers(options?.headers);
    expect(headers.get('Content-Type')).toBe('application/json');
    expect(headers.get('X-XSRF-TOKEN')).toBe('test-token-value');
  });
});
