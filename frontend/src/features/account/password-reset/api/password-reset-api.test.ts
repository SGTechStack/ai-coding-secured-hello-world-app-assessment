import { describe, expect, it } from 'vitest';
import { axiosFailure } from '../../../../test-support';
import { toPasswordResetRejection } from './password-reset-api';

describe('toPasswordResetRejection', () => {
  it('maps an invalid reset token to an invalid reset link', () => {
    expect(toPasswordResetRejection(axiosFailure(400, { code: 'PASSWORD_RESET_TOKEN_INVALID' })).kind).toBe(
      'invalid-reset-link',
    );
  });

  it('maps any other 400 to rejected with its field errors', () => {
    const rejection = toPasswordResetRejection(
      axiosFailure(400, { errors: [{ field: 'newPassword', code: 'PASSWORD_REUSED' }] }),
    );

    expect(rejection.kind).toBe('rejected');
    expect(rejection.errors).toEqual([{ field: 'newPassword', code: 'PASSWORD_REUSED' }]);
  });
});
