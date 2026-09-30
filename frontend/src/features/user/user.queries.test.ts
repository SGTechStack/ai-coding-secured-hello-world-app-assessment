import { describe, it, expect, vi, beforeEach } from 'vitest';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { renderHook, waitFor } from '@testing-library/react';
import React from 'react';
import { currentUserQueryOptions, useCurrentUser, getInitials } from './user.queries';

vi.mock('@/api/generated/current-user-profile-controller/current-user-profile-controller', () => ({
  getCurrentUserProfile: vi.fn(),
  getGetCurrentUserProfileQueryKey: () => ['/api/v1/me'],
}));

import { getCurrentUserProfile } from '@/api/generated/current-user-profile-controller/current-user-profile-controller';
const mockGetCurrentUserProfile = vi.mocked(getCurrentUserProfile);

function makeProfile(overrides: Partial<{ username: string; displayName: string; roles: string[] }> = {}) {
  return {
    data: {
      username: 'jsmith',
      displayName: 'John Smith',
      roles: ['user'],
      ...overrides,
    },
  };
}

function makeWrapper() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return {
    client,
    wrapper: ({ children }: { children: React.ReactNode }) =>
      React.createElement(QueryClientProvider, { client }, children),
  };
}

describe('currentUserQueryOptions', () => {
  it('uses the generated query key', () => {
    const opts = currentUserQueryOptions();
    expect(opts.queryKey).toEqual(['/api/v1/me']);
  });

  it('sets staleTime to 5 minutes', () => {
    const opts = currentUserQueryOptions();
    expect(opts.staleTime).toBe(5 * 60 * 1000);
  });

  it('queryFn maps profile fields to User shape', async () => {
    mockGetCurrentUserProfile.mockResolvedValue(makeProfile() as never);
    const opts = currentUserQueryOptions();
    const result = await opts.queryFn!({} as never);
    expect(result).toEqual({ username: 'jsmith', displayName: 'John Smith', roles: ['user'] });
  });

  it('queryFn falls back to empty strings and empty array when profile fields are undefined', async () => {
    mockGetCurrentUserProfile.mockResolvedValue(
      makeProfile({
        username: undefined as never,
        displayName: undefined as never,
        roles: undefined as never,
      }) as never,
    );
    const opts = currentUserQueryOptions();
    const result = await opts.queryFn!({} as never);
    expect(result).toEqual({ username: '', displayName: '', roles: [] });
  });
});

describe('useCurrentUser', () => {
  beforeEach(() => {
    mockGetCurrentUserProfile.mockReset();
  });

  it('returns the mapped user on success', async () => {
    mockGetCurrentUserProfile.mockResolvedValue(makeProfile({ roles: ['admin'] }) as never);
    const { wrapper } = makeWrapper();

    const { result } = renderHook(() => useCurrentUser(), { wrapper });

    await waitFor(() => expect(result.current.data).toBeDefined());
    expect(result.current.data).toEqual({
      username: 'jsmith',
      displayName: 'John Smith',
      roles: ['admin'],
    });
  });
});

describe('getInitials', () => {
  it('returns initials from two-word name', () => {
    expect(getInitials('John Smith')).toBe('JS');
  });

  it('returns single initial for one-word name', () => {
    expect(getInitials('Alice')).toBe('A');
  });

  it('caps at two characters for longer names', () => {
    expect(getInitials('Mary Jane Watson')).toBe('MJ');
  });

  it('uppercases the result', () => {
    expect(getInitials('john smith')).toBe('JS');
  });

  it('returns empty string for empty input', () => {
    expect(getInitials('')).toBe('');
  });
});
