import { describe, expect, it } from 'vitest';
import { axiosFailure } from '../../../../test-support';
import { toLoginRejection } from './rejection';

describe('toLoginRejection', () => {
  it('maps a 401 to an Authentication failure', () => {
    expect(toLoginRejection(axiosFailure(401)).kind).toBe('invalid-credentials');
  });
});
