import { afterEach, describe, expect, it, vi } from 'vitest';

import {
  csrfHeader,
  ensureCsrfToken,
  refreshAnonymousCsrfToken,
  resetCsrfToken,
  startAuthenticatedCsrfSession,
} from './csrf';
import { csrfToken as token } from '../../test-support';

describe('CSRF token lifecycle', () => {
  afterEach(() => {
    resetCsrfToken();
  });

  it('holds the anonymous token only in memory', async () => {
    await ensureCsrfToken(() => Promise.resolve(token('anonymous-token')));

    expect(csrfHeader()).toEqual({ name: 'X-CSRF-TOKEN', value: 'anonymous-token' });
    expect(document.cookie).not.toContain('anonymous-token');
  });

  it('shares one fetch between concurrent callers and never fetches while a token is held', async () => {
    const fetchToken = vi.fn(() => Promise.resolve(token('shared-token')));

    await Promise.all([ensureCsrfToken(fetchToken), ensureCsrfToken(fetchToken)]);
    await ensureCsrfToken(fetchToken);

    expect(fetchToken).toHaveBeenCalledOnce();
  });

  it('fetches again after a failed fetch', async () => {
    const fetchToken = vi.fn().mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce(token('retry-token'));

    await expect(ensureCsrfToken(fetchToken)).rejects.toThrow('offline');
    await ensureCsrfToken(fetchToken);

    expect(csrfHeader()?.value).toBe('retry-token');
  });

  it("drops the anonymous token on login and fetches the authenticated session's token next", async () => {
    await ensureCsrfToken(() => Promise.resolve(token('anonymous-token')));

    startAuthenticatedCsrfSession();
    expect(csrfHeader()).toBeUndefined();
    await ensureCsrfToken(() => Promise.resolve(token('login-token')));

    expect(csrfHeader()?.value).toBe('login-token');
  });

  it('discards an anonymous fetch that completes after login', async () => {
    let resolve!: (value: ReturnType<typeof token>) => void;
    const late = ensureCsrfToken(
      () =>
        new Promise((done) => {
          resolve = done;
        }),
    );

    startAuthenticatedCsrfSession();
    resolve(token('stale-anonymous-token'));
    await late;

    expect(csrfHeader()).toBeUndefined();
  });

  it('replaces an anonymous token once, however many refused requests report it', async () => {
    await ensureCsrfToken(() => Promise.resolve(token('expired-token')));
    const fetchToken = vi.fn(() => Promise.resolve(token('fresh-token')));

    const retries = await Promise.all([
      refreshAnonymousCsrfToken('expired-token', fetchToken),
      refreshAnonymousCsrfToken('expired-token', fetchToken),
    ]);
    const late = await refreshAnonymousCsrfToken('expired-token', fetchToken);

    expect([...retries, late]).toEqual([true, true, true]);
    expect(fetchToken).toHaveBeenCalledOnce();
    expect(csrfHeader()?.value).toBe('fresh-token');
  });

  it("never re-fetches the authenticated session's token", async () => {
    startAuthenticatedCsrfSession();
    await ensureCsrfToken(() => Promise.resolve(token('login-token')));
    const fetchToken = vi.fn(() => Promise.resolve(token('other')));

    expect(await refreshAnonymousCsrfToken('login-token', fetchToken)).toBe(false);
    expect(fetchToken).not.toHaveBeenCalled();
  });
});
