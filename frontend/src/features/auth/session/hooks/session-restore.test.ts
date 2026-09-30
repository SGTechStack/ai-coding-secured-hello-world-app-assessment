import { QueryClient } from '@tanstack/react-query';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { apiClient } from '../../../../common/http/api-client';
import { csrfHeader, refreshAnonymousCsrfToken, resetCsrfToken, type CsrfResponse } from '../../../../common/http/csrf';
import type { ProblemDetailJson } from '../../../../common/http/problem-detail';
import { csrfToken, fakeAdapter, JOHNDOE, type FakeReply } from '../../../../test-support';
import type { Profile } from '../../../account/profile';
import { readSession } from '../queries/session';
import { restoreSession, SessionRestoreError } from './session-lifecycle';

const PROFILE = { id: JOHNDOE.id, role: 'USER' };
const TOKEN = csrfToken();
// `Omit<Profile, 'id' | 'role'>` covers the contract-breaking profiles (no id, no role) the restore must reject.
type ReplyJson = Profile | Omit<Profile, 'id'> | Omit<Profile, 'role'> | CsrfResponse | ProblemDetailJson;
type Reply = FakeReply<ReplyJson>;

let queryClient: QueryClient;
let calls: string[];

/** Answers each URL with its reply; anything unlisted is a test error. */
function serve(replies: Record<string, Reply>) {
  apiClient.defaults.adapter = fakeAdapter((config) => {
    calls.push(`${config.method?.toUpperCase()} ${config.url}`);
    const reply = replies[config.url ?? ''];
    if (!reply) throw new Error(`unexpected request ${config.url}`);
    return reply;
  });
}

const original = apiClient.defaults.adapter;
beforeEach(() => {
  queryClient = new QueryClient();
  calls = [];
});
afterEach(() => {
  resetCsrfToken();
  apiClient.defaults.adapter = original;
});

describe('restoreSession', () => {
  it("checks the profile first, then holds the live Session's CSRF token and seeds the Session", async () => {
    serve({ '/api/profile': { status: 200, data: PROFILE }, '/csrf': { status: 200, data: TOKEN } });

    await expect(restoreSession(queryClient)).resolves.toBe(true);

    expect(calls).toEqual(['GET /api/profile', 'GET /csrf']);
    expect(readSession(queryClient)).toEqual(PROFILE);
    expect(csrfHeader()).toEqual({ name: 'X-CSRF-TOKEN', value: 'session-token' });
    // The token belongs to the authenticated Session, so a later 403 is never "fixed" by re-fetching it.
    await expect(refreshAnonymousCsrfToken('session-token', () => Promise.resolve(TOKEN))).resolves.toBe(false);
  });

  it('reports no Session and fetches no CSRF token when the profile is Authentication-required', async () => {
    serve({ '/api/profile': { status: 401, data: { code: 'AUTHENTICATION_REQUIRED' } } });

    await expect(restoreSession(queryClient)).resolves.toBe(false);

    expect(calls).toEqual(['GET /api/profile']);
    expect(readSession(queryClient)).toBeUndefined();
    expect(csrfHeader()).toBeUndefined();
  });

  it.each<[string, Record<string, Reply>]>([
    ['the profile request has a network error', { '/api/profile': 'network-error' }],
    ['the profile request fails on the server', { '/api/profile': { status: 500, data: {} } }],
    ['the profile does not match the contract', { '/api/profile': { status: 200, data: { role: 'USER' } } }],
    ['the profile carries no role', { '/api/profile': { status: 200, data: { id: JOHNDOE.id } } }],
    ['the CSRF token cannot be fetched', { '/api/profile': { status: 200, data: PROFILE }, '/csrf': 'network-error' }],
  ])('fails with a retryable SessionRestoreError, restoring nothing, when %s', async (_, replies) => {
    serve(replies);

    await expect(restoreSession(queryClient)).rejects.toBeInstanceOf(SessionRestoreError);

    expect(readSession(queryClient)).toBeUndefined();
    expect(csrfHeader()).toBeUndefined();
  });
});
