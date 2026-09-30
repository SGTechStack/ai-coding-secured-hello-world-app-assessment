import { beforeEach, describe, expect, it, vi } from 'vitest';

import { axiosFailure } from '../../../../test-support';
import type { MessageResponse } from '../../core';
import type { RegistrationRequest } from '../model/registration';

// The success body, or one that breaks the contract (to prove it is parsed, not trusted).
type RegistrationReplyJson = MessageResponse | { unexpected: true };
const post = vi.fn<(url: string, body: RegistrationRequest) => Promise<{ data: RegistrationReplyJson }>>();
vi.mock('../../../../common/http/api-client', () => ({
  apiClient: { post: (url: string, body: RegistrationRequest) => post(url, body) },
}));

import { registerAccount, toRegistrationRejection } from './registration-api';

beforeEach(() => {
  post.mockReset().mockResolvedValue({ data: { message: 'Account created. You can now log in.' } });
});

describe('registerAccount', () => {
  it('posts only the three registration fields', async () => {
    const input = { username: 'testuser123', email: 'testuser123@test.example.com', password: 'Str0ng!Passw0rd' };

    await registerAccount(input);

    expect(post).toHaveBeenCalledWith('/api/auth/register', input);
  });

  it('rejects a success response that does not match the contract', async () => {
    post.mockResolvedValueOnce({ data: { unexpected: true } });

    const failure: unknown = await registerAccount({ username: 'a', email: 'b', password: 'c' }).catch(
      (error: unknown) => error,
    );

    expect(toRegistrationRejection(failure).kind).toBe('unavailable');
  });
});

describe('toRegistrationRejection', () => {
  it('maps a duplicate account', () => {
    expect(toRegistrationRejection(axiosFailure(400, { errors: [{ code: 'USER_EXISTS' }] })).kind).toBe('user-exists');
  });

  it('maps any other 400 to rejected with its field errors', () => {
    const rejection = toRegistrationRejection(
      axiosFailure(400, { errors: [{ field: 'email', code: 'EMAIL_INVALID' }] }),
    );

    expect(rejection.kind).toBe('rejected');
    expect(rejection.errors).toEqual([{ field: 'email', code: 'EMAIL_INVALID' }]);
  });
});
