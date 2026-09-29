import { afterEach, describe, expect, it, vi } from 'vitest';
import { api, ApiError } from './api';

afterEach(() => vi.unstubAllGlobals());

describe('API transport', () => {
  it('includes credentials and a fresh CSRF token on each mutation', async () => {
    const fetcher = vi
      .fn()
      .mockResolvedValueOnce(Response.json({ token: 'fresh-token', headerName: 'X-CSRF-TOKEN' }))
      .mockResolvedValueOnce(Response.json({ username: 'jingshun' }));
    vi.stubGlobal('fetch', fetcher);
    const result = await api.post('/auth/login', {
      username: 'jingshun',
      password: 'a-private-password',
    });
    expect(result).toEqual({ username: 'jingshun' });
    expect(fetcher).toHaveBeenNthCalledWith(
      1,
      expect.stringContaining('/api/auth/csrf'),
      expect.objectContaining({ credentials: 'include' }),
    );
    expect(fetcher).toHaveBeenNthCalledWith(
      2,
      expect.stringContaining('/api/auth/login'),
      expect.objectContaining({
        credentials: 'include',
        method: 'POST',
        headers: expect.objectContaining({ 'X-CSRF-TOKEN': 'fresh-token' }),
      }),
    );
  });

  it('preserves validation details and status without leaking raw server errors', async () => {
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValue(
          Response.json(
            { message: 'Check fields', errors: { email: 'Invalid email' } },
            { status: 400 },
          ),
        ),
    );
    await expect(api.get('/auth/me')).rejects.toMatchObject({
      status: 400,
      message: 'Check fields',
      fields: { email: 'Invalid email' },
    });
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(new Response('<html>internal traceback</html>', { status: 500 })),
    );
    await expect(api.get('/auth/me')).rejects.toThrow(ApiError);
    await expect(api.get('/auth/me')).rejects.toThrow('Something went wrong. Please try again.');
  });

  it('handles no-content logout responses', async () => {
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValueOnce(Response.json({ token: 'token', headerName: 'X-CSRF-TOKEN' }))
        .mockResolvedValueOnce(new Response(null, { status: 204 })),
    );
    await expect(api.post('/auth/logout')).resolves.toBeUndefined();
  });
});
