import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { API_BASE_URL, ApiError, CSRF_FAILURE_DETAIL, apiFetch, resetCsrfToken } from './client';

type FetchMock = ReturnType<typeof vi.fn>;

function jsonResponse(status: number, body: unknown, contentType = 'application/json'): Response {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: body === undefined ? {} : { 'Content-Type': contentType },
  });
}

const CSRF = { headerName: 'X-CSRF-TOKEN', token: 'tok-1' };

describe('apiFetch', () => {
  let fetchMock: FetchMock;

  beforeEach(() => {
    resetCsrfToken();
    fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('sends GET requests with credentials and without a CSRF header', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(200, { message: 'Hello, alice' }));

    const result = await apiFetch<{ message: string }>('/api/hello');

    expect(result).toEqual({ message: 'Hello, alice' });
    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe(`${API_BASE_URL}/api/hello`);
    expect(init.credentials).toBe('include');
    expect(init.method).toBe('GET');
    expect((init.headers as Record<string, string>)['X-CSRF-TOKEN']).toBeUndefined();
  });

  it('fetches the CSRF token once and attaches it to mutating requests', async () => {
    fetchMock
      .mockResolvedValueOnce(jsonResponse(200, CSRF))
      .mockResolvedValueOnce(jsonResponse(200, { authenticated: true }))
      .mockResolvedValueOnce(jsonResponse(200, { ok: true }));

    await apiFetch('/api/auth/login', { method: 'POST', body: { username: 'a', password: 'b' } });
    await apiFetch('/api/other', { method: 'PATCH', body: { x: 1 } });

    expect(fetchMock).toHaveBeenCalledTimes(3);
    expect((fetchMock.mock.calls[0] as [string])[0]).toBe(`${API_BASE_URL}/api/auth/csrf`);
    const [, loginInit] = fetchMock.mock.calls[1] as [string, RequestInit];
    const headers = loginInit.headers as Record<string, string>;
    expect(headers['X-CSRF-TOKEN']).toBe('tok-1');
    expect(headers['Content-Type']).toBe('application/json');
    expect(loginInit.body).toBe(JSON.stringify({ username: 'a', password: 'b' }));
    expect(loginInit.credentials).toBe('include');
    const [, patchInit] = fetchMock.mock.calls[2] as [string, RequestInit];
    expect((patchInit.headers as Record<string, string>)['X-CSRF-TOKEN']).toBe('tok-1');
  });

  it('refreshes the CSRF token and retries once when the server rejects it', async () => {
    fetchMock
      .mockResolvedValueOnce(jsonResponse(200, CSRF))
      .mockResolvedValueOnce(
        jsonResponse(403, { status: 403, detail: CSRF_FAILURE_DETAIL }, 'application/problem+json'),
      )
      .mockResolvedValueOnce(jsonResponse(200, { headerName: 'X-CSRF-TOKEN', token: 'tok-2' }))
      .mockResolvedValueOnce(jsonResponse(204, undefined));

    const result = await apiFetch<void>('/api/auth/logout', { method: 'POST' });

    expect(result).toBeUndefined();
    expect(fetchMock).toHaveBeenCalledTimes(4);
    const [, retryInit] = fetchMock.mock.calls[3] as [string, RequestInit];
    expect((retryInit.headers as Record<string, string>)['X-CSRF-TOKEN']).toBe('tok-2');
  });

  it('does not retry a 403 that is not a CSRF failure', async () => {
    fetchMock
      .mockResolvedValueOnce(jsonResponse(200, CSRF))
      .mockResolvedValueOnce(
        jsonResponse(403, { status: 403, detail: 'Access denied' }, 'application/problem+json'),
      );

    await expect(apiFetch('/api/admin/users/1', { method: 'DELETE' })).rejects.toMatchObject({
      status: 403,
      message: 'Access denied',
    });
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it('turns problem responses into ApiError with field errors', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse(
        409,
        {
          status: 409,
          title: 'Conflict',
          detail: 'Username is already taken',
          errors: [{ field: 'username', message: 'Username is already taken' }],
        },
        'application/problem+json',
      ),
    );

    const error = await apiFetch('/api/auth/me').catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    const apiError = error as ApiError;
    expect(apiError.status).toBe(409);
    expect(apiError.message).toBe('Username is already taken');
    expect(apiError.fieldErrors).toEqual({ username: 'Username is already taken' });
    expect(apiError.isCsrfFailure()).toBe(false);
  });

  it('copes with non-JSON error bodies', async () => {
    fetchMock.mockResolvedValueOnce(new Response('<html>gateway</html>', { status: 502 }));

    const error = (await apiFetch('/api/hello').catch((e: unknown) => e)) as ApiError;

    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(502);
    expect(error.message).toBe('Request failed with status 502');
  });
});
