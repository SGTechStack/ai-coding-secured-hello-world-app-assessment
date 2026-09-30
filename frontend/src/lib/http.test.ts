import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { http, HttpError, toApiError } from './http';
import { RETURN_TO_KEY } from './auth';

function makeResponse(status: number, body?: unknown): Response {
  return {
    status,
    ok: status >= 200 && status < 300,
    json: () => Promise.resolve(body),
    headers: new Headers(),
  } as unknown as Response;
}

describe('HttpError', () => {
  it('sets status and message', () => {
    const err = new HttpError(404);
    expect(err.status).toBe(404);
    expect(err.message).toBe('HTTP 404');
  });

  it('sets body when provided', () => {
    const body = { detail: 'Not found' };
    const err = new HttpError(404, body);
    expect(err.body).toEqual(body);
  });

  it('is an instance of Error', () => {
    expect(new HttpError(500)).toBeInstanceOf(Error);
  });
});

describe('toApiError', () => {
  it('converts HttpError using detail', () => {
    const err = new HttpError(400, { detail: 'bad input' });
    expect(() => toApiError(err)).toThrow('bad input');
  });

  it('falls back to title when detail is absent', () => {
    const err = new HttpError(400, { title: 'Bad Request' });
    expect(() => toApiError(err)).toThrow('Bad Request');
  });

  it('falls back to HTTP status when neither detail nor title is present', () => {
    const err = new HttpError(503, {});
    expect(() => toApiError(err)).toThrow('HTTP 503');
  });

  it('propagates fieldErrors', () => {
    const fieldErrors = { email: { code: 'invalid', message: 'bad email' } };
    const err = new HttpError(422, { fieldErrors });
    let thrown: unknown;
    try {
      toApiError(err);
    } catch (e) {
      thrown = e;
    }
    expect((thrown as { fieldErrors: unknown }).fieldErrors).toEqual(fieldErrors);
  });

  it('re-throws non-HttpError as-is', () => {
    const raw = new Error('network failure');
    expect(() => toApiError(raw)).toThrow(raw);
  });
});

describe('http()', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn());
    Object.defineProperty(document, 'cookie', { value: '', writable: true });
  });

  afterEach(() => {
    vi.unstubAllEnvs();
  });

  it('calls fetch with the given url and init', async () => {
    const mockFetch = vi.mocked(fetch);
    mockFetch.mockResolvedValue(makeResponse(200));

    await http('/api/test', { method: 'GET' });

    expect(mockFetch).toHaveBeenCalledWith('/api/test', expect.objectContaining({ method: 'GET' }));
  });

  it('attaches X-XSRF-TOKEN header for POST when cookie is present', async () => {
    Object.defineProperty(document, 'cookie', { value: 'XSRF-TOKEN=abc123', writable: true });
    const mockFetch = vi.mocked(fetch);
    mockFetch.mockResolvedValue(makeResponse(200));

    await http('/api/save', { method: 'POST' });

    const init = mockFetch.mock.calls[0][1] as RequestInit;
    expect((init.headers as Record<string, string>)['X-XSRF-TOKEN']).toBe('abc123');
  });

  it('attaches X-XSRF-TOKEN for PUT, PATCH, DELETE', async () => {
    Object.defineProperty(document, 'cookie', { value: 'XSRF-TOKEN=tok', writable: true });
    const mockFetch = vi.mocked(fetch);
    mockFetch.mockResolvedValue(makeResponse(200));

    for (const method of ['PUT', 'PATCH', 'DELETE']) {
      mockFetch.mockClear();
      await http('/api/x', { method });
      const init = mockFetch.mock.calls[0][1] as RequestInit;
      expect((init.headers as Record<string, string>)['X-XSRF-TOKEN']).toBe('tok');
    }
  });

  it('does not attach X-XSRF-TOKEN for GET even when cookie is present', async () => {
    Object.defineProperty(document, 'cookie', { value: 'XSRF-TOKEN=tok', writable: true });
    const mockFetch = vi.mocked(fetch);
    mockFetch.mockResolvedValue(makeResponse(200));

    await http('/api/x', { method: 'GET' });

    const init = mockFetch.mock.calls[0][1] as RequestInit;
    expect((init.headers as Record<string, string> | undefined)?.['X-XSRF-TOKEN']).toBeUndefined();
  });

  it('does not attach X-XSRF-TOKEN when no cookie', async () => {
    Object.defineProperty(document, 'cookie', { value: '', writable: true });
    const mockFetch = vi.mocked(fetch);
    mockFetch.mockResolvedValue(makeResponse(200));

    await http('/api/save', { method: 'POST' });

    const init = mockFetch.mock.calls[0][1] as RequestInit;
    expect((init.headers as Record<string, string> | undefined)?.['X-XSRF-TOKEN']).toBeUndefined();
  });

  it('redirects to the configured login url on 401', async () => {
    vi.stubEnv('VITE_LOGIN_URL', '/oauth2/authorization/aas');
    vi.mocked(fetch).mockResolvedValue(makeResponse(401));
    const hrefSetter = vi.fn();
    Object.defineProperty(window, 'location', {
      configurable: true,
      value: {
        pathname: '/secure',
        search: '',
        hash: '',
        set href(value: string) {
          hrefSetter(value);
        },
      },
    });

    await http('/api/secure');

    expect(hrefSetter).toHaveBeenCalledWith('/oauth2/authorization/aas');
    expect(sessionStorage.getItem(RETURN_TO_KEY)).toBe('/secure');
  });

  it('does not redirect on non-401', async () => {
    vi.mocked(fetch).mockResolvedValue(makeResponse(403));
    const hrefSetter = vi.fn();
    Object.defineProperty(window, 'location', {
      configurable: true,
      value: {
        pathname: '/secure',
        search: '',
        hash: '',
        set href(value: string) {
          hrefSetter(value);
        },
      },
    });

    await http('/api/secure');

    expect(hrefSetter).not.toHaveBeenCalled();
  });

  it('returns the response', async () => {
    const res = makeResponse(200);
    vi.mocked(fetch).mockResolvedValue(res);

    const result = await http('/api/test');

    expect(result).toBe(res);
  });
});
