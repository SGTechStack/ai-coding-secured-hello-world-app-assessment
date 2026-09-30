import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, renderHook, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { apiClient } from '../../../../common/http/api-client';
import {
  csrfHeader,
  ensureCsrfToken,
  resetCsrfToken,
  startAuthenticatedCsrfSession,
  type CsrfResponse,
} from '../../../../common/http/csrf';
import { csrfToken, fakeAdapter, type FakeReply, JOHNDOE as profile } from '../../../../test-support';
import { readSession, seedSession } from '../../session';

const navigate = vi.fn<(options: { to: string }) => Promise<void>>();
vi.mock('@tanstack/react-router', () => ({ useRouter: () => ({ navigate }) }));

import { useLogout } from './useLogout';

const LOGOUT = '/api/auth/logout';
type Reply = FakeReply<CsrfResponse>;

/** Fake server behind the real transport: `/csrf` hands out the next token, logout gets the next queued reply. */
function serve(tokens: string[], replies: Reply[]) {
  const sent: { url?: string; csrf?: string }[] = [];
  apiClient.defaults.adapter = fakeAdapter((config) => {
    sent.push({ url: config.url, csrf: config.headers.get('X-CSRF-TOKEN') as string | undefined });
    return config.url === '/csrf'
      ? { status: 200, data: csrfToken(tokens.shift() ?? 'no-token-queued') }
      : (replies.shift() ?? { status: 204 });
  });
  return {
    logouts: () => sent.filter((request) => request.url === LOGOUT).map((request) => request.csrf),
    csrfFetches: () => sent.filter((request) => request.url === '/csrf').length,
  };
}

let queryClient: QueryClient;
const wrapper = ({ children }: { children: ReactNode }) => (
  <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
);

async function logOut() {
  const { result } = renderHook(() => useLogout(), { wrapper });
  await act(async () => {
    await result.current.mutateAsync().catch(() => undefined);
  });
  return result;
}

function expectSessionEnded() {
  expect(readSession(queryClient)).toBeUndefined();
  expect(queryClient.getQueryData(['private', profile.id])).toBeUndefined();
  expect(csrfHeader()).toBeUndefined();
  expect(navigate).toHaveBeenCalledExactlyOnceWith({ to: '/login' });
}

const original = apiClient.defaults.adapter;
beforeEach(async () => {
  queryClient = new QueryClient();
  navigate.mockReset().mockResolvedValue(undefined);
  seedSession(queryClient, profile);
  queryClient.setQueryData(['private', profile.id], 'private data');
  startAuthenticatedCsrfSession();
  await ensureCsrfToken(() => Promise.resolve(csrfToken()));
});
afterEach(() => {
  resetCsrfToken();
  apiClient.defaults.adapter = original;
});

describe('useLogout', () => {
  it('ends the Session on the client when the server ends it (204)', async () => {
    const server = serve([], [{ status: 204 }]);

    await logOut();

    expect(server.logouts()).toEqual(['session-token']);
    expectSessionEnded();
  });

  it('ends the Session on the client when the server reports there was none (401)', async () => {
    serve([], [{ status: 401 }]);

    const result = await logOut();

    expectSessionEnded();
    expect(result.current.isError).toBe(false);
  });

  it.each([204, 401])(
    'refetches the CSRF token once after a 403 and ends the Session when the retry gets %i',
    async (status) => {
      const server = serve(['fresh-token'], [{ status: 403 }, { status }]);

      await logOut();

      expect(server.logouts()).toEqual(['session-token', 'fresh-token']);
      expect(server.csrfFetches()).toBe(1);
      expectSessionEnded();
    },
  );

  it('ends the Session without a second retry when the retry is also refused with a 403', async () => {
    const server = serve(['fresh-token', 'unused-token'], [{ status: 403 }, { status: 403 }]);

    await logOut();

    expect(server.logouts()).toEqual(['session-token', 'fresh-token']);
    expect(server.csrfFetches()).toBe(1);
    expectSessionEnded();
  });

  it.each<[string, Reply]>([
    ['a server error', { status: 503 }],
    ['a network failure', 'network-error'],
  ])('keeps the User signed in and exposes an error after %s', async (_failure, reply) => {
    serve([], [reply]);

    const result = await logOut();

    // TanStack Query notifies observers on a macrotask, so the error state may land just after the mutation settles.
    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(readSession(queryClient)).toEqual(profile);
    expect(queryClient.getQueryData(['private', profile.id])).toBe('private data');
    expect(csrfHeader()?.value).toBe('session-token');
    expect(navigate).not.toHaveBeenCalled();
  });

  it('never lets a response that arrives after Logout repopulate the cache', async () => {
    serve([], [{ status: 204 }]);
    let resolveLate!: (value: string) => void;
    void queryClient
      .fetchQuery({
        queryKey: ['private', 'late'],
        queryFn: () =>
          new Promise<string>((resolve) => {
            resolveLate = resolve;
          }),
      })
      .catch(() => undefined);

    await logOut();
    await act(async () => {
      resolveLate('late private data');
      await Promise.resolve();
    });

    expect(queryClient.getQueryData(['private', 'late'])).toBeUndefined();
  });
});
