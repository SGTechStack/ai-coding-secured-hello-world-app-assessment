import { describe, it, expect, vi } from 'vitest';
import { requireRole } from './auth';

vi.mock('@tanstack/react-router', () => ({
  redirect: vi.fn((opts: unknown) => opts),
}));

describe('requireRole', () => {
  it('does not throw when role is present', () => {
    expect(() => requireRole(['admin', 'user'], 'admin')).not.toThrow();
  });

  it('throws when role is absent', () => {
    expect(() => requireRole(['admin'], 'user')).toThrow();
  });

  it('throws redirect to /forbidden', () => {
    let thrown: unknown;
    try {
      requireRole([], 'admin');
    } catch (e) {
      thrown = e;
    }
    expect(thrown).toMatchObject({ to: '/forbidden' });
  });

  it('does not throw when roles list contains the required role among others', () => {
    expect(() => requireRole(['viewer', 'editor', 'admin'], 'editor')).not.toThrow();
  });
});
