import { QueryClient } from '@tanstack/react-query';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { apiClient, onSessionExpired } from '../../../../common/http/api-client';
import {
  csrfHeader,
  ensureCsrfToken,
  resetCsrfToken,
  startAuthenticatedCsrfSession,
} from '../../../../common/http/csrf';
import type { ProblemDetailJson } from '../../../../common/http/problem-detail';
import { csrfToken, fakeAdapter, type FakeReply, JOHNDOE as profile } from '../../../../test-support';
import { readSession, seedSession } from '../queries/session';
import { endSession } from './session-lifecycle';

const navigate = vi.fn<(options: { to: string }) => Promise<void>>();
let queryClient: QueryClient;

/** Every request except `/api/late` is refused with the Problem Detail `code`; `/api/late` answers when released. */
function refuseWith(status: number, code: string) {
  let release!: () => void;
  const late = new Promise<void>((resolve) => {
    release = resolve;
  });
  // A refusal carries a Problem Detail; `/api/late` answers with the plain string its query reads.
  apiClient.defaults.adapter = fakeAdapter(async (config): Promise<FakeReply<ProblemDetailJson | string>> => {
    if (config.url !== '/api/late') return { status, data: { code } };
    await late;
    return { status: 200, data: 'late private data' };
  });
  return release;
}

const original = apiClient.defaults.adapter;
beforeEach(async () => {
  queryClient = new QueryClient();
  navigate.mockReset().mockResolvedValue(undefined);
  onSessionExpired(() => void endSession(queryClient, { navigate }));
  seedSession(queryClient, profile);
  queryClient.setQueryData(['private', profile.id], 'private data');
  startAuthenticatedCsrfSession();
  await ensureCsrfToken(() => Promise.resolve(csrfToken()));
});
afterEach(() => {
  onSessionExpired(undefined);
  resetCsrfToken();
  apiClient.defaults.adapter = original;
});

describe('Session expired through the auth-lifecycle owner', () => {
  it.each([
    [401, 'AUTHENTICATION_REQUIRED'],
    [403, 'CSRF_TOKEN_REJECTED'],
  ])(
    'ends the Session once for concurrent %i %s rejections, and a late response cannot repopulate it',
    async (status, code) => {
      const release = refuseWith(status, code);
      const late = queryClient
        .fetchQuery({
          queryKey: ['private', 'late'],
          queryFn: async () => (await apiClient.get<string>('/api/late')).data,
        })
        .catch(() => undefined);

      await Promise.allSettled([apiClient.get('/api/a'), apiClient.post('/api/b', {}), apiClient.delete('/api/c')]);
      await vi.waitFor(() => expect(navigate).toHaveBeenCalled());
      release();
      await late;

      expect(navigate).toHaveBeenCalledExactlyOnceWith({ to: '/login' });
      expect(readSession(queryClient)).toBeUndefined();
      expect(queryClient.getQueryData(['private', profile.id])).toBeUndefined();
      expect(queryClient.getQueryData(['private', 'late'])).toBeUndefined();
      expect(csrfHeader()).toBeUndefined();
    },
  );

  it('keeps the User signed in after an ACCESS_DENIED 403', async () => {
    refuseWith(403, 'ACCESS_DENIED');

    await Promise.allSettled([apiClient.get('/api/admin/users'), apiClient.post('/api/admin/users', {})]);

    expect(navigate).not.toHaveBeenCalled();
    expect(readSession(queryClient)).toEqual(profile);
    expect(queryClient.getQueryData(['private', profile.id])).toBe('private data');
    expect(csrfHeader()?.value).toBe('session-token');
  });
});
