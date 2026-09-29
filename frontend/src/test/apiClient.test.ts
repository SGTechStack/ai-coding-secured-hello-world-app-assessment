import { ApiError, getCsrfToken, apiFetch } from '../api/client';
import { mockProblemDetail, mockResponse, setCsrfCookie } from './helpers';

describe('getCsrfToken', () => {
  it('returns null when no XSRF-TOKEN cookie is set', () => {
    expect(getCsrfToken()).toBeNull();
  });

  it('extracts token from the XSRF-TOKEN cookie', () => {
    setCsrfCookie('abc123');
    expect(getCsrfToken()).toBe('abc123');
  });

  it('decodes URI-encoded token values', () => {
    document.cookie = 'XSRF-TOKEN=tok%3Den; path=/';
    expect(getCsrfToken()).toBe('tok=en');
  });
});

describe('apiFetch CSRF header', () => {
  it('sends X-XSRF-TOKEN on POST requests', async () => {
    setCsrfCookie('csrf-value');
    const spy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(mockResponse({ ok: true }));

    await apiFetch('/api/test', { method: 'POST', body: JSON.stringify({}) });

    const [, init] = spy.mock.calls[0] as [string, RequestInit];
    const headers = init.headers as Record<string, string>;
    expect(headers['X-XSRF-TOKEN']).toBe('csrf-value');
  });

  it('does NOT send X-XSRF-TOKEN on GET requests', async () => {
    setCsrfCookie('csrf-value');
    const spy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(mockResponse({ ok: true }));

    await apiFetch('/api/test', { method: 'GET' });

    const [, init] = spy.mock.calls[0] as [string, RequestInit];
    const headers = init.headers as Record<string, string>;
    expect(headers['X-XSRF-TOKEN']).toBeUndefined();
  });

  it('always includes credentials: include', async () => {
    const spy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(mockResponse({}));

    await apiFetch('/api/test');

    const [, init] = spy.mock.calls[0] as [string, RequestInit];
    expect(init.credentials).toBe('include');
  });
});

describe('apiFetch error handling', () => {
  it('throws ApiError for 401 responses', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(mockProblemDetail(401, 'Invalid credentials'));

    await expect(apiFetch('/api/test')).rejects.toBeInstanceOf(ApiError);
  });

  it('throws ApiError with status for 403 responses', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(mockProblemDetail(403, 'Forbidden'));

    const err = await apiFetch('/api/test').catch((e: unknown) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect((err as ApiError).status).toBe(403);
    expect((err as ApiError).isForbidden).toBe(true);
  });

  it('throws ApiError for 409 with isConflict true', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(mockProblemDetail(409, 'Conflict'));

    const err = await apiFetch('/api/test').catch((e: unknown) => e);
    expect((err as ApiError).isConflict).toBe(true);
  });

  it('throws ApiError for 429 with isTooManyRequests true', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(mockProblemDetail(429, 'Too many requests'));

    const err = await apiFetch('/api/test').catch((e: unknown) => e);
    expect((err as ApiError).isTooManyRequests).toBe(true);
  });
});
