import { apiClient } from '../../../../common/http/api-client';
import { toRejection, type Rejection } from '../../../../common/http/rejection';
import { messageSchema, type MessageResponse } from '../../core';
import type { PasswordResetConfirmRequest, PasswordResetRequest } from '../model/password-reset';

// Failures propagate raw; the form hooks map them with toPasswordResetRejection.

/** Asks for a Password reset link. The answer is the same whether or not the email is registered. */
export async function requestPasswordReset(input: PasswordResetRequest): Promise<MessageResponse> {
  return messageSchema.parse((await apiClient.post<MessageResponse>('/api/auth/password-reset', input)).data);
}

/** Completes a Password reset: 204 once the password is changed. Never authenticates; the Visitor logs in afterwards. */
export async function confirmPasswordReset(input: PasswordResetConfirmRequest): Promise<void> {
  await apiClient.post('/api/auth/password-reset/confirm', input);
}

/** A failed Password reset step: a dead link, field errors, or one of the global kinds. */
export type PasswordResetRejection = Rejection<'invalid-reset-link' | 'rejected'>;

export const toPasswordResetRejection = (error: unknown): PasswordResetRejection =>
  toRejection(error, ({ status, code }) => {
    if (status !== 400) return undefined;
    // An unknown, expired or used Password reset link: the server never says which (ADR 0004).
    return code === 'PASSWORD_RESET_TOKEN_INVALID' ? 'invalid-reset-link' : 'rejected';
  });
