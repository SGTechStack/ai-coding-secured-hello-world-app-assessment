import { AxiosError } from 'axios';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { csrfToken, fakeAdapter } from '../../test-support';
import { apiClient, onSessionExpired, WITHOUT_CSRF_RETRY } from './api-client';
import { csrfHeader, resetCsrfToken, startAuthenticatedCsrfSession, type CsrfResponse } from './csrf';
import type { ProblemDetailJson } from './problem-detail';

type Reply = { status: number; data?: CsrfResponse | ProblemDetailJson };

/** Fake server: `/csrf` hands out the next token; every other request gets the next queued reply (default 201). */
function fakeServer(tokens: string[], replies: Reply[] = []) {
  const sent: { url?: string; method?: string; csrf?: string }[] = [];
  const adapter = fakeAdapter((config) => {
    sent.push({ url: config.url, method: config.method, csrf: config.headers.get('X-ADVERTISED-CSRF') as string });
    return config.url === '/csrf'
      ? { status: 200, data: { ...csrfToken(tokens.shift() ?? 'no-token-queued'), headerName: 'X-ADVERTISED-CSRF' } }
      : (replies.shift() ?? { status: 201 });
  });
  return { adapter, sent };
}

describe('API client CSRF transport', () => {
  const original = apiClient.defaults.adapter;
  let server: ReturnType<typeof fakeServer>;
  const serve = (tokens: string[], replies?: Reply[]) => {
    server = fakeServer(tokens, replies);
    apiClient.defaults.adapter = server.adapter;
  };

  beforeEach(() => {
    serve([]);
  });
  afterEach(() => {
    resetCsrfToken();
    apiClient.defaults.adapter = original;
  });

  it('fetches the anonymous token through the transport on the first mutating request only', async () => {
    serve(['anonymous-token']);

    await apiClient.get('/api/anything');
    await apiClient.post('/api/auth/register', {});
    await apiClient.post('/api/auth/register', {});

    expect(server.sent).toEqual([
      { url: '/api/anything', method: 'get', csrf: undefined },
      { url: '/csrf', method: 'get', csrf: undefined },
      { url: '/api/auth/register', method: 'post', csrf: 'anonymous-token' },
      { url: '/api/auth/register', method: 'post', csrf: 'anonymous-token' },
    ]);
  });

  it('shares one token fetch between concurrent first requests', async () => {
    serve(['anonymous-token']);

    await Promise.all([apiClient.post('/api/a', {}), apiClient.post('/api/b', {})]);

    expect(server.sent.filter((request) => request.url === '/csrf')).toHaveLength(1);
  });

  it('fetches the authenticated token from /csrf after login, and never retries a refused authenticated request', async () => {
    serve(['anonymous-token', 'login-token'], [{ status: 200 }, { status: 403 }]);
    await apiClient.post('/api/auth/login', {});
    startAuthenticatedCsrfSession();

    await expect(apiClient.post('/api/admin/users', {})).rejects.toMatchObject({ response: { status: 403 } });

    expect(server.sent).toEqual([
      { url: '/csrf', method: 'get', csrf: undefined },
      { url: '/api/auth/login', method: 'post', csrf: 'anonymous-token' },
      { url: '/csrf', method: 'get', csrf: undefined },
      { url: '/api/admin/users', method: 'post', csrf: 'login-token' },
    ]);
  });

  it('replaces an expired anonymous token once and retries each refused request once', async () => {
    serve(['expired-token', 'fresh-token'], [{ status: 403 }, { status: 403 }]);

    const responses = await Promise.all([apiClient.post('/api/a', {}), apiClient.post('/api/b', {})]);

    expect(responses.map((response) => response.status)).toEqual([201, 201]);
    expect(server.sent.filter((request) => request.url === '/csrf')).toHaveLength(2);
    expect(
      server.sent
        .filter((request) => request.url !== '/csrf')
        .map((request) => request.csrf ?? '')
        .sort((a, b) => a.localeCompare(b)),
    ).toEqual(['expired-token', 'expired-token', 'fresh-token', 'fresh-token']);
  });

  it('does not retry other failures', async () => {
    serve(['anonymous-token'], [{ status: 400 }]);

    await expect(apiClient.post('/api/auth/register', {})).rejects.toMatchObject({ response: { status: 400 } });
    expect(server.sent.filter((request) => request.url !== '/csrf')).toHaveLength(1);
  });

  describe('Session expired', () => {
    const problem = (status: number, code: string): Reply => ({ status, data: { code } });
    const expired = vi.fn();
    beforeEach(() => {
      expired.mockReset();
      onSessionExpired(expired);
    });
    afterEach(() => {
      onSessionExpired(undefined);
    });

    const logIn = async (tokens: string[], replies: Reply[]) => {
      serve(['anonymous-token', ...tokens], [{ status: 200 }, ...replies]);
      await apiClient.post('/api/auth/login', {});
      startAuthenticatedCsrfSession();
    };

    it.each([
      ['a read', 'get'],
      ['a mutating call', 'post'],
    ])('reports an AUTHENTICATION_REQUIRED 401 on %s after login, and drops the token', async (_call, method) => {
      await logIn(['session-token'], [problem(401, 'AUTHENTICATION_REQUIRED')]);

      await expect(apiClient.request({ url: '/api/anything', method })).rejects.toMatchObject({
        response: { status: 401 },
      });

      expect(expired).toHaveBeenCalledOnce();
      expect(csrfHeader()).toBeUndefined();
    });

    it('reports a CSRF_TOKEN_REJECTED 403 after login without retrying it', async () => {
      await logIn(['session-token'], [problem(403, 'CSRF_TOKEN_REJECTED')]);

      await expect(apiClient.post('/api/anything', {})).rejects.toMatchObject({ response: { status: 403 } });

      expect(expired).toHaveBeenCalledOnce();
      expect(server.sent.filter((request) => request.url === '/api/anything')).toHaveLength(1);
    });

    it('never reports an ACCESS_DENIED 403', async () => {
      await logIn(['session-token'], [problem(403, 'ACCESS_DENIED'), problem(403, 'ACCESS_DENIED')]);

      await expect(apiClient.get('/api/admin/users')).rejects.toMatchObject({ response: { status: 403 } });
      await expect(apiClient.post('/api/admin/users', {})).rejects.toMatchObject({ response: { status: 403 } });

      expect(expired).not.toHaveBeenCalled();
      expect(csrfHeader()?.value).toBe('session-token');
    });

    it('never reports a wrong-credentials 401 from the login endpoint', async () => {
      serve(['anonymous-token'], [problem(401, 'INVALID_CREDENTIALS')]);

      await expect(apiClient.post('/api/auth/login', {})).rejects.toMatchObject({ response: { status: 401 } });

      expect(expired).not.toHaveBeenCalled();
    });

    it('tells rejections apart by code, not status: a 401 or 403 without a known code is not reported', async () => {
      await logIn(['session-token'], [{ status: 401 }, { status: 403 }]);

      await expect(apiClient.get('/api/anything')).rejects.toMatchObject({ response: { status: 401 } });
      await expect(apiClient.post('/api/anything', {})).rejects.toMatchObject({ response: { status: 403 } });

      expect(expired).not.toHaveBeenCalled();
    });

    it('keeps the anonymous rule before login: a CSRF_TOKEN_REJECTED 403 is retried once with a fresh token', async () => {
      serve(['expired-token', 'fresh-token'], [problem(403, 'CSRF_TOKEN_REJECTED')]);

      await expect(apiClient.post('/api/auth/register', {})).resolves.toMatchObject({ status: 201 });

      expect(expired).not.toHaveBeenCalled();
    });

    it('reports several concurrent rejections only once', async () => {
      await logIn(
        ['session-token'],
        [
          problem(401, 'AUTHENTICATION_REQUIRED'),
          problem(403, 'CSRF_TOKEN_REJECTED'),
          problem(401, 'AUTHENTICATION_REQUIRED'),
        ],
      );

      await Promise.allSettled([apiClient.get('/api/a'), apiClient.post('/api/b', {}), apiClient.get('/api/c')]);

      expect(expired).toHaveBeenCalledOnce();
    });

    it('leaves a request that handles its own rejections, like Logout, to its caller', async () => {
      await logIn(['session-token'], [problem(403, 'CSRF_TOKEN_REJECTED'), problem(401, 'AUTHENTICATION_REQUIRED')]);

      await expect(apiClient.post('/api/auth/logout', undefined, WITHOUT_CSRF_RETRY)).rejects.toBeDefined();
      await expect(apiClient.post('/api/auth/logout', undefined, WITHOUT_CSRF_RETRY)).rejects.toBeDefined();

      expect(expired).not.toHaveBeenCalled();
      expect(csrfHeader()?.value).toBe('session-token');
    });
  });

  it('does not send the request when the token cannot be fetched', async () => {
    serve([]);
    server.adapter.mockImplementationOnce((config) => Promise.reject(new AxiosError('offline', 'ERR_NETWORK', config)));

    await expect(apiClient.post('/api/auth/register', {})).rejects.toThrow('offline');
    expect(server.adapter).toHaveBeenCalledOnce();
  });
});
