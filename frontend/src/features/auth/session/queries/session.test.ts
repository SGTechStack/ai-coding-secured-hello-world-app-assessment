import { QueryClient } from '@tanstack/react-query';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { JOHNDOE as profile } from '../../../../test-support';
import { readSession, seedSession } from './session';

afterEach(() => {
  vi.useRealTimers();
});

describe('session', () => {
  it('keeps a seeded Session with no observers past the default garbage-collection time', async () => {
    vi.useFakeTimers();
    const queryClient = new QueryClient();

    seedSession(queryClient, profile);
    await vi.advanceTimersByTimeAsync(60 * 60 * 1000);

    expect(readSession(queryClient)).toEqual(profile);
  });
});
