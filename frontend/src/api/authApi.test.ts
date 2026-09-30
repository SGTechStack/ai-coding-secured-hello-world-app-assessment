import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { type FetchMock, jsonResponse, stubFetchWithCsrf } from '../test/fetchMock';
import {
  InvalidCredentialsError,
  RegistrationError,
  ServerUnavailableError,
  login,
  logout,
  register,
} from './authApi';
import { MESSAGES, RateLimitedError } from './errors';

function csrfFetchCount() {
  return vi.mocked(fetch).mock.calls.filter(([input]) => String(input).endsWith('/api/auth/csrf')).length;
}

describe('login', () => {
  let api: FetchMock;

  beforeEach(() => {
    api = stubFetchWithCsrf();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('resolves when the server accepts the credentials', async () => {
    api.mockResolvedValue(jsonResponse(200, {}));

    await expect(login({ username: 'johndoe', password: 'Password123!' })).resolves.toBeUndefined();

    expect(api).toHaveBeenCalledWith(
      '/api/auth/login',
      expect.objectContaining({
        method: 'POST',
        credentials: 'include',
        body: JSON.stringify({ username: 'johndoe', password: 'Password123!' }),
      }),
    );
  });

  it('drops the cached CSRF token after a successful login (the server rotates it)', async () => {
    api.mockImplementation(() => Promise.resolve(jsonResponse(200, {})));

    await login({ username: 'johndoe', password: 'Password123!' });
    await login({ username: 'johndoe', password: 'Password123!' });

    expect(csrfFetchCount()).toBe(2);
  });

  it('throws InvalidCredentialsError with the fixed generic message on a 401, ignoring the body', async () => {
    api.mockResolvedValue(jsonResponse(401, { code: 'INVALID_CREDENTIALS', message: 'Account is locked' }));

    const error = await login({ username: 'johndoe', password: 'wrong' }).catch((e: unknown) => e);

    expect(error).toBeInstanceOf(InvalidCredentialsError);
    expect((error as Error).message).toBe('Invalid username or password');
  });

  it('throws RateLimitedError on a 429', async () => {
    api.mockResolvedValue(jsonResponse(429, { code: 'RATE_LIMITED' }));

    await expect(login({ username: 'johndoe', password: 'x' })).rejects.toBeInstanceOf(RateLimitedError);
  });

  it('throws ServerUnavailableError on a 5xx', async () => {
    api.mockResolvedValue(jsonResponse(503, {}));

    await expect(login({ username: 'johndoe', password: 'Password123!' })).rejects.toBeInstanceOf(
      ServerUnavailableError,
    );
  });

  it('throws ServerUnavailableError when the network request itself fails', async () => {
    api.mockRejectedValue(new TypeError('Failed to fetch'));

    await expect(login({ username: 'johndoe', password: 'Password123!' })).rejects.toBeInstanceOf(
      ServerUnavailableError,
    );
  });
});

describe('logout', () => {
  let api: FetchMock;

  beforeEach(() => {
    api = stubFetchWithCsrf();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('resolves on 200 and clears the cached CSRF token', async () => {
    api.mockImplementation(() => Promise.resolve(new Response(null, { status: 200 })));

    await logout();
    await logout();

    expect(csrfFetchCount()).toBe(2);
  });

  it('treats a 401 (session already gone) as logged out', async () => {
    api.mockResolvedValue(jsonResponse(401, {}));

    await expect(logout()).resolves.toBeUndefined();
  });

  it('throws on a 5xx', async () => {
    api.mockResolvedValue(jsonResponse(500, {}));

    await expect(logout()).rejects.toThrow(MESSAGES.logoutFailed);
  });

  it('throws on a network failure', async () => {
    api.mockRejectedValue(new TypeError('Failed to fetch'));

    await expect(logout()).rejects.toThrow(MESSAGES.logoutFailed);
  });
});

describe('register', () => {
  let api: FetchMock;
  const input = { username: 'johndoe', email: 'j@example.com', password: 'Password123!', firstName: 'J' };

  beforeEach(() => {
    api = stubFetchWithCsrf();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('shows the fixed merged conflict text on a 409, whatever the server says', async () => {
    api.mockResolvedValue(jsonResponse(409, { code: 'CONFLICT', message: 'Email taken <b>x</b>' }));

    await expect(register(input)).rejects.toThrow(new RegistrationError('Username or email is already registered'));
  });

  it('passes through the message of a 400 VALIDATION_FAILED', async () => {
    api.mockResolvedValue(jsonResponse(400, { code: 'VALIDATION_FAILED', message: 'Username is invalid' }));

    await expect(register(input)).rejects.toThrow('Username is invalid');
  });

  it('uses a fixed message for any other 400 body', async () => {
    api.mockResolvedValue(jsonResponse(400, { code: 'SOMETHING_ELSE', message: 'raw server text' }));

    await expect(register(input)).rejects.toThrow(MESSAGES.registrationInvalid);
  });

  it('shows the rate-limit message on a 429', async () => {
    api.mockResolvedValue(jsonResponse(429, {}));

    await expect(register(input)).rejects.toThrow(MESSAGES.rateLimited);
  });
});
