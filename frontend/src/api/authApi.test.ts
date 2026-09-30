import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { InvalidCredentialsError, ServerUnavailableError, TooManyAttemptsError, login } from './authApi';

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

describe('login', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('resolves when the server accepts the credentials', async () => {
    vi.mocked(fetch).mockResolvedValue(jsonResponse(200, {}));

    await expect(login({ username: 'johndoe', password: 'Password123!' })).resolves.toBeUndefined();

    expect(fetch).toHaveBeenCalledWith(
      '/api/auth/login',
      expect.objectContaining({
        method: 'POST',
        credentials: 'include',
        body: JSON.stringify({ username: 'johndoe', password: 'Password123!' }),
      }),
    );
  });

  it('throws InvalidCredentialsError on a 401', async () => {
    vi.mocked(fetch).mockResolvedValue(
      jsonResponse(401, { message: 'Invalid username or password' }),
    );

    await expect(login({ username: 'johndoe', password: 'wrong' })).rejects.toBeInstanceOf(
      InvalidCredentialsError,
    );
  });

  it('throws TooManyAttemptsError on a 429', async () => {
    vi.mocked(fetch).mockResolvedValue(new Response(null, { status: 429 }));

    await expect(login({ username: 'johndoe', password: 'wrong' })).rejects.toBeInstanceOf(
      TooManyAttemptsError,
    );
  });

  it('throws ServerUnavailableError on a 5xx', async () => {
    vi.mocked(fetch).mockResolvedValue(jsonResponse(503, {}));

    await expect(login({ username: 'johndoe', password: 'Password123!' })).rejects.toBeInstanceOf(
      ServerUnavailableError,
    );
  });

  it('throws ServerUnavailableError when the network request itself fails', async () => {
    vi.mocked(fetch).mockRejectedValue(new TypeError('Failed to fetch'));

    await expect(login({ username: 'johndoe', password: 'Password123!' })).rejects.toBeInstanceOf(
      ServerUnavailableError,
    );
  });
});
