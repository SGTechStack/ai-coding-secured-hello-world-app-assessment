import { QueryClient } from '@tanstack/react-query';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import type { Greeting } from '../api/greeting-api';

// The Greeting body, or one that breaks the contract (to prove it is parsed, not trusted).
const get = vi.fn<(url: string) => Promise<{ data: Greeting | { unexpected: true } }>>();
vi.mock('../../../../common/http/api-client', () => ({ apiClient: { get: (url: string) => get(url) } }));

import { greetingQueryOptions } from './greeting';

beforeEach(() => {
  get.mockReset();
});

describe('greetingQueryOptions', () => {
  it('scopes the Greeting to the User and waits until the identity is known', () => {
    expect(greetingQueryOptions('user-1')).toMatchObject({ queryKey: ['greeting', 'user-1'], enabled: true });
    expect(greetingQueryOptions(undefined)).toMatchObject({ enabled: false });
  });

  it('fails at once, without retrying, so the Home page can offer its own retry', async () => {
    get.mockRejectedValue(new Error('server error'));

    await expect(new QueryClient().fetchQuery(greetingQueryOptions('user-1'))).rejects.toThrow('server error');
    expect(get).toHaveBeenCalledOnce();
  });

  it('reads the Greeting from GET /api/hello', async () => {
    get.mockResolvedValue({ data: { message: 'Hello, johndoe' } });

    await expect(new QueryClient().fetchQuery(greetingQueryOptions('user-1'))).resolves.toEqual({
      message: 'Hello, johndoe',
    });
    expect(get).toHaveBeenCalledWith('/api/hello');
  });

  it('rejects a response that does not match the contract', async () => {
    get.mockResolvedValue({ data: { unexpected: true } });

    await expect(
      new QueryClient({ defaultOptions: { queries: { retry: false } } }).fetchQuery(greetingQueryOptions('user-1')),
    ).rejects.toThrow();
  });
});
