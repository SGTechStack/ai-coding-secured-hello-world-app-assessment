import { describe, it, expect } from 'vitest';
import { createQueryClient } from './query-client';

describe('createQueryClient', () => {
  it('sets staleTime to 60 seconds', () => {
    const client = createQueryClient();
    expect(client.getDefaultOptions().queries?.staleTime).toBe(1000 * 60);
  });

  it('sets retry to 1', () => {
    const client = createQueryClient();
    expect(client.getDefaultOptions().queries?.retry).toBe(1);
  });

  it('returns a new instance on each call', () => {
    const a = createQueryClient();
    const b = createQueryClient();
    expect(a).not.toBe(b);
  });
});
