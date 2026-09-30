import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, renderHook } from '@testing-library/react';
import type { ReactNode } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { csrfHeader, ensureCsrfToken, resetCsrfToken } from '../../../../common/http/csrf';
import { csrfToken, JOHNDOE } from '../../../../test-support';
import type { LoginInput, LoginResponse } from '../model/login';
import { readSession } from '../../session';

const invalidate = vi.fn<() => Promise<void>>();
/** A login body, or one that breaks the contract by carrying no role. */
type LoginJson = LoginResponse | { profile: Omit<LoginResponse['profile'], 'role'> };
const post = vi.fn<(url: string, body: LoginInput) => Promise<{ data: LoginJson }>>();
vi.mock('@tanstack/react-router', () => ({ useRouter: () => ({ invalidate }) }));
vi.mock('../../../../common/http/api-client', () => ({
  apiClient: { post: (url: string, body: LoginInput) => post(url, body) },
}));

import { useLogin } from './useLogin';

const credentials = { username: 'johndoe', password: 'Password123!' };
const loginResponse = { profile: JOHNDOE };
const anonymousToken = csrfToken('anonymous-token');

let queryClient: QueryClient;
const wrapper = ({ children }: { children: ReactNode }) => (
  <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
);

beforeEach(() => {
  queryClient = new QueryClient();
  invalidate.mockReset().mockResolvedValue(undefined);
  post.mockReset().mockResolvedValue({ data: loginResponse });
});
afterEach(() => {
  resetCsrfToken();
});

describe('useLogin', () => {
  it('drops the anonymous CSRF token, seeds the Session, then re-evaluates route guards', async () => {
    await ensureCsrfToken(() => Promise.resolve(anonymousToken));
    const { result } = renderHook(() => useLogin(), { wrapper });

    await act(async () => {
      await result.current.mutateAsync(credentials);
    });

    expect(post).toHaveBeenCalledWith('/api/auth/login', credentials);
    expect(csrfHeader()).toBeUndefined();
    expect(readSession(queryClient)).toEqual({ id: JOHNDOE.id, role: 'USER' });
    expect(invalidate).toHaveBeenCalledOnce();
  });

  it('treats a login response without a role as a broken contract and seeds nothing', async () => {
    post.mockResolvedValueOnce({ data: { profile: { id: JOHNDOE.id, username: JOHNDOE.username } } });
    const { result } = renderHook(() => useLogin(), { wrapper });

    await act(async () => {
      await expect(result.current.mutateAsync(credentials)).rejects.toThrow();
    });

    expect(readSession(queryClient)).toBeUndefined();
    expect(invalidate).not.toHaveBeenCalled();
  });

  it('changes nothing when the login is refused', async () => {
    await ensureCsrfToken(() => Promise.resolve(anonymousToken));
    post.mockRejectedValueOnce(new Error('refused'));
    const { result } = renderHook(() => useLogin(), { wrapper });

    await act(async () => {
      await expect(result.current.mutateAsync(credentials)).rejects.toThrow('refused');
    });

    expect(csrfHeader()?.value).toBe('anonymous-token');
    expect(readSession(queryClient)).toBeUndefined();
  });
});
