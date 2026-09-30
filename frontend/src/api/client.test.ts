import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError, createApiClient } from './client';

const BASE_URL = 'https://api.test.example.com/api';

type Route = (init: RequestInit) => Response;

interface Call {
  path: string;
  init: RequestInit;
}

/** A stand-in for the API: routes by method and path, and records every call. */
function fakeBackend(routes: Record<string, Route>) {
  const calls: Call[] = [];
  const fetch = vi.fn(async (input: RequestInfo | URL, init: RequestInit = {}) => {
    const path = String(input).replace(BASE_URL, '');
    calls.push({ path, init });
    const route = routes[`${init.method ?? 'GET'} ${path}`];
    if (!route) throw new Error(`Unexpected request: ${init.method ?? 'GET'} ${path}`);
    return route(init);
  });
  const client = createApiClient({ baseUrl: BASE_URL, fetch });
  const header = (call: Call, name: string) => new Headers(call.init.headers).get(name);
  return { client, calls, header };
}

function json(body: unknown, status = 200, contentType = 'application/json') {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': contentType } });
}

/** Mimics the API: a new CSRF token is issued after every login. */
function csrfIssuer() {
  let issued = 0;
  return {
    route: () => json({ token: `token-${++issued}`, headerName: 'X-CSRF-TOKEN', parameterName: '_csrf' }),
  };
}

/** A Problem Details error response, as the API sends. */
function problem(status: number, code: string) {
  return json({ status, code }, status, 'application/problem+json');
}

/** A Throttled response, as the API sends, with whatever `Retry-After` the test gives. */
function throttled(retryAfter?: string) {
  const headers: Record<string, string> = { 'Content-Type': 'application/problem+json' };
  if (retryAfter !== undefined) headers['Retry-After'] = retryAfter;
  return new Response(JSON.stringify({ status: 429, code: 'too many requests' }), { status: 429, headers });
}

const ok = () => new Response(null, { status: 200 });
const created = () => new Response(null, { status: 201 });

/** A registration to send as "the next state-changing request" after the step under test. */
const TESTUSER2 = { username: 'testuser2', email: 'testuser2@test.example.com', password: 'a-long-password-2' };

describe('API client', () => {
  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
  });

  it('fetches the CSRF token on start, sending credentials', async () => {
    const { client, calls } = fakeBackend({ 'GET /csrf': csrfIssuer().route });

    await client.start();

    expect(calls).toHaveLength(1);
    expect(calls[0].path).toBe('/csrf');
    expect(calls[0].init.credentials).toBe('include');
  });

  it('attaches the CSRF token to state-changing requests', async () => {
    const { client, calls, header } = fakeBackend({ 'GET /csrf': csrfIssuer().route, 'POST /register': created });
    await client.start();

    await client.register({ username: 'testuser1', email: 'testuser1@test.example.com', password: 'a-long-password-1' });

    const register = calls[1];
    expect(header(register, 'X-CSRF-TOKEN')).toBe('token-1');
    expect(header(register, 'Content-Type')).toBe('application/json');
    expect(register.init.credentials).toBe('include');
    expect(JSON.parse(String(register.init.body))).toEqual({
      username: 'testuser1',
      email: 'testuser1@test.example.com',
      password: 'a-long-password-1',
    });
  });

  it('logs in with a form post, then fetches a new CSRF token and uses it', async () => {
    const { client, calls, header } = fakeBackend({
      'GET /csrf': csrfIssuer().route,
      'POST /login': ok,
      'POST /register': created,
    });
    await client.start();

    await client.login('testuser1', 'a-long-password-1');
    await client.register(TESTUSER2);

    expect(calls.map((call) => `${call.init.method ?? 'GET'} ${call.path}`)).toEqual([
      'GET /csrf',
      'POST /login',
      'GET /csrf',
      'POST /register',
    ]);
    const login = calls[1];
    expect(header(login, 'X-CSRF-TOKEN')).toBe('token-1');
    expect(header(login, 'Content-Type')).toBe('application/x-www-form-urlencoded');
    expect(new URLSearchParams(String(login.init.body))).toEqual(
      new URLSearchParams({ username: 'testuser1', password: 'a-long-password-1' }),
    );
    expect(header(calls[3], 'X-CSRF-TOKEN')).toBe('token-2');
  });

  it('still succeeds when the token refresh after login fails, and fetches a token before the next change', async () => {
    const issuer = csrfIssuer();
    let csrfFailing = false;
    const { client, calls, header } = fakeBackend({
      'GET /csrf': () => (csrfFailing ? new Response('Bad gateway', { status: 502 }) : issuer.route()),
      'POST /login': () => {
        csrfFailing = true;
        return ok();
      },
      'POST /register': created,
    });
    await client.start();

    await expect(client.login('testuser1', 'a-long-password-1')).resolves.toBeUndefined();

    csrfFailing = false;
    await client.register(TESTUSER2);
    expect(calls.map((call) => `${call.init.method ?? 'GET'} ${call.path}`)).toEqual([
      'GET /csrf',
      'POST /login',
      'GET /csrf',
      'GET /csrf',
      'POST /register',
    ]);
    // Never the pre-login token: it belongs to the old session.
    expect(header(calls[4], 'X-CSRF-TOKEN')).toBe('token-2');
  });

  it('fetches a CSRF token first when a state-changing request comes before start', async () => {
    const { client, calls, header } = fakeBackend({ 'GET /csrf': csrfIssuer().route, 'POST /register': created });

    await client.register({ username: 'testuser1', email: 'testuser1@test.example.com', password: 'a-long-password-1' });

    expect(calls.map((call) => call.path)).toEqual(['/csrf', '/register']);
    expect(header(calls[1], 'X-CSRF-TOKEN')).toBe('token-1');
  });

  it('sends reads with credentials and without a CSRF token', async () => {
    const me = { id: '3f1c9a1e-0000-4000-8000-000000000001', username: 'testuser1', email: 'testuser1@test.example.com', role: 'USER' };
    const { client, calls, header } = fakeBackend({
      'GET /csrf': csrfIssuer().route,
      'GET /hello': () => new Response('Hello, testuser1', { headers: { 'Content-Type': 'text/plain' } }),
      'GET /me': () => json(me),
    });
    await client.start();

    expect(await client.hello()).toBe('Hello, testuser1');
    expect(await client.me()).toEqual(me);

    for (const read of calls.slice(1)) {
      expect(read.init.credentials).toBe('include');
      expect(header(read, 'X-CSRF-TOKEN')).toBeNull();
    }
  });

  it('turns a Problem Details response into an ApiError with its code and field errors', async () => {
    const problem = {
      type: 'about:blank',
      title: 'Bad Request',
      status: 400,
      detail: 'The request is invalid.',
      code: 'validation failed',
      errors: [{ field: 'email', message: 'must be a well-formed email address' }],
    };
    const { client } = fakeBackend({
      'GET /csrf': csrfIssuer().route,
      'POST /register': () => json(problem, 400, 'application/problem+json'),
    });
    await client.start();

    const error = await client
      .register({ username: 'testuser1', email: 'not-an-email', password: 'a-long-password-1' })
      .catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).problem).toEqual(problem);
    expect((error as ApiError).status).toBe(400);
    expect((error as ApiError).code).toBe('validation failed');
  });

  it('reads how long a Throttled request must wait from Retry-After', async () => {
    const { client } = fakeBackend({ 'GET /csrf': csrfIssuer().route, 'POST /login': () => throttled('30') });

    const error = await client.login('testuser1', 'a-long-password-1').catch((e: unknown) => e);

    expect((error as ApiError).code).toBe('too many requests');
    expect((error as ApiError).problem.retryAfterSeconds).toBe(30);
  });

  it('leaves the wait unknown when Retry-After is missing or not a number of seconds', async () => {
    for (const retryAfter of [undefined, 'Wed, 21 Oct 2026 07:28:00 GMT', '-5', '']) {
      const { client } = fakeBackend({ 'GET /me': () => throttled(retryAfter) });

      const error = await client.me().catch((e: unknown) => e);

      expect((error as ApiError).problem.retryAfterSeconds).toBeUndefined();
    }
  });

  it('reports an error without a Problem Details body by its status', async () => {
    const { client } = fakeBackend({ 'GET /me': () => new Response('Bad gateway', { status: 502 }) });

    const error = await client.me().catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).status).toBe(502);
    expect((error as ApiError).code).toBe('error');
  });

  it('logs out with the CSRF token, then fetches a new token and uses it', async () => {
    const { client, calls, header } = fakeBackend({
      'GET /csrf': csrfIssuer().route,
      'POST /logout': ok,
      'POST /register': created,
    });
    await client.start();

    await client.logout();
    await client.register(TESTUSER2);

    expect(calls.map((call) => `${call.init.method ?? 'GET'} ${call.path}`)).toEqual([
      'GET /csrf',
      'POST /logout',
      'GET /csrf',
      'POST /register',
    ]);
    expect(header(calls[1], 'X-CSRF-TOKEN')).toBe('token-1');
    expect(calls[1].init.credentials).toBe('include');
    expect(header(calls[3], 'X-CSRF-TOKEN')).toBe('token-2');
  });

  it('drops the old CSRF token even when logout is rejected, as it is on an expired session', async () => {
    const { client, calls, header } = fakeBackend({
      'GET /csrf': csrfIssuer().route,
      'POST /logout': () => problem(403, 'forbidden'),
      'POST /register': created,
    });
    await client.start();

    await expect(client.logout()).rejects.toBeInstanceOf(ApiError);

    await client.register(TESTUSER2);
    expect(header(calls.at(-1)!, 'X-CSRF-TOKEN')).toBe('token-2');
  });

  describe('global 401 handler', () => {
    const unauthenticated = () => problem(401, 'unauthenticated');

    it('is called when a request comes back 401, and the request still rejects', async () => {
      const { client } = fakeBackend({ 'GET /hello': unauthenticated });
      const onUnauthenticated = vi.fn();
      client.setUnauthenticatedHandler(onUnauthenticated);

      const error = await client.hello().catch((e: unknown) => e);

      expect(onUnauthenticated).toHaveBeenCalledOnce();
      expect((error as ApiError).status).toBe(401);
    });

    it('is not called for a failed login, which is an answer rather than a lost session', async () => {
      const { client } = fakeBackend({
        'GET /csrf': csrfIssuer().route,
        'POST /login': () => problem(401, 'invalid credentials'),
      });
      const onUnauthenticated = vi.fn();
      client.setUnauthenticatedHandler(onUnauthenticated);

      await expect(client.login('testuser1', 'wrong-password-1')).rejects.toBeInstanceOf(ApiError);

      expect(onUnauthenticated).not.toHaveBeenCalled();
    });

    it('is not called for other errors', async () => {
      const { client } = fakeBackend({ 'GET /me': () => problem(403, 'forbidden') });
      const onUnauthenticated = vi.fn();
      client.setUnauthenticatedHandler(onUnauthenticated);

      await client.me().catch(() => undefined);

      expect(onUnauthenticated).not.toHaveBeenCalled();
    });

    it('drops the lost session’s CSRF token, so the next change fetches a new one', async () => {
      const { client, calls, header } = fakeBackend({
        'GET /csrf': csrfIssuer().route,
        'GET /hello': unauthenticated,
        'POST /register': created,
      });
      await client.start();

      await client.hello().catch(() => undefined);
      await client.register(TESTUSER2);

      expect(calls.map((call) => `${call.init.method ?? 'GET'} ${call.path}`)).toEqual([
        'GET /csrf',
        'GET /hello',
        'GET /csrf',
        'POST /register',
      ]);
      expect(header(calls[3], 'X-CSRF-TOKEN')).toBe('token-2');
    });
  });

  describe('password reset', () => {
    it('requests a reset link and sets a new password, each with the CSRF token', async () => {
      const { client, calls, header } = fakeBackend({
        'GET /csrf': csrfIssuer().route,
        'POST /password-reset/request': () => json({ message: 'If that email is registered, …' }, 202),
        'POST /password-reset/confirm': ok,
      });
      await client.start();

      await client.requestPasswordReset('testuser1@test.example.com');
      await client.confirmPasswordReset('a-reset-token', 'a-brand-new-password-1');

      const [request, confirm] = calls.slice(1);
      expect(request.path).toBe('/password-reset/request');
      expect(JSON.parse(String(request.init.body))).toEqual({ email: 'testuser1@test.example.com' });
      expect(confirm.path).toBe('/password-reset/confirm');
      expect(JSON.parse(String(confirm.init.body))).toEqual({
        token: 'a-reset-token',
        newPassword: 'a-brand-new-password-1',
      });
      for (const call of [request, confirm]) {
        expect(call.init.method).toBe('POST');
        expect(header(call, 'X-CSRF-TOKEN')).toBe('token-1');
        expect(header(call, 'Content-Type')).toBe('application/json');
        expect(call.init.credentials).toBe('include');
      }
    });

    it('rejects a spent or expired token with its stable code', async () => {
      const { client } = fakeBackend({
        'GET /csrf': csrfIssuer().route,
        'POST /password-reset/confirm': () => problem(400, 'password reset token expired or invalid'),
      });

      const error = await client.confirmPasswordReset('used-token', 'a-brand-new-password-1').catch((e: unknown) => e);

      expect((error as ApiError).code).toBe('password reset token expired or invalid');
    });
  });

  describe('account administration', () => {
    const ID = '3f1c9a1e-0000-4000-8000-000000000002';
    const bob = {
      id: ID,
      username: 'testuser2',
      email: 'testuser2@test.example.com',
      role: 'USER',
      enabled: true,
      locked: false,
      createdAt: '2026-01-05T09:00:00Z',
    };

    it('lists Accounts a page at a time, as a read without a CSRF token', async () => {
      const page = { content: [bob], page: { size: 20, number: 1, totalElements: 21, totalPages: 2 } };
      const { client, calls, header } = fakeBackend({ 'GET /admin/users?page=1&size=20': () => json(page) });

      expect(await client.listAccounts({ page: 1, size: 20 })).toEqual(page);

      expect(calls[0].init.credentials).toBe('include');
      expect(header(calls[0], 'X-CSRF-TOKEN')).toBeNull();
    });

    it('sends each change with its method, body and the CSRF token, and returns the updated Account', async () => {
      const { client, calls, header } = fakeBackend({
        'GET /csrf': csrfIssuer().route,
        [`PATCH /admin/users/${ID}/status`]: () => json({ ...bob, enabled: false }),
        [`POST /admin/users/${ID}/unlock`]: () => json(bob),
        [`PATCH /admin/users/${ID}/role`]: () => json({ ...bob, role: 'ADMIN' }),
        [`DELETE /admin/users/${ID}`]: () => new Response(null, { status: 204 }),
      });
      await client.start();

      expect(await client.setAccountEnabled(ID, false)).toEqual({ ...bob, enabled: false });
      expect(await client.unlockAccount(ID)).toEqual(bob);
      expect(await client.changeAccountRole(ID, 'ADMIN')).toEqual({ ...bob, role: 'ADMIN' });
      await client.deleteAccount(ID);

      const [status, unlock, role, remove] = calls.slice(1);
      expect(JSON.parse(String(status.init.body))).toEqual({ enabled: false });
      expect(unlock.init.body).toBeUndefined();
      expect(JSON.parse(String(role.init.body))).toEqual({ role: 'ADMIN' });
      expect(remove.init.method).toBe('DELETE');
      for (const call of [status, unlock, role, remove]) {
        expect(header(call, 'X-CSRF-TOKEN')).toBe('token-1');
        expect(call.init.credentials).toBe('include');
      }
    });

    it('escapes the Account id into the path', async () => {
      const { client, calls } = fakeBackend({
        'GET /csrf': csrfIssuer().route,
        'POST /admin/users/..%2Fcsrf/unlock': () => problem(400, 'validation failed'),
      });

      await client.unlockAccount('../csrf').catch(() => undefined);

      expect(calls.at(-1)!.path).toBe('/admin/users/..%2Fcsrf/unlock');
    });

    it('reports a refused change on the caller’s own Account by its stable code', async () => {
      const { client } = fakeBackend({
        'GET /csrf': csrfIssuer().route,
        [`DELETE /admin/users/${ID}`]: () => problem(403, 'self action not allowed'),
      });

      const error = await client.deleteAccount(ID).catch((e: unknown) => e);

      expect((error as ApiError).code).toBe('self action not allowed');
    });
  });

  it('keeps the CSRF token out of browser storage', async () => {
    const { client } = fakeBackend({ 'GET /csrf': csrfIssuer().route, 'POST /login': ok });

    await client.start();
    await client.login('testuser1', 'a-long-password-1');

    expect(localStorage.length).toBe(0);
    expect(sessionStorage.length).toBe(0);
    expect(document.cookie).toBe('');
  });
});
