import { apiClient } from '../../../../common/http/api-client';
import { toRejection, type Rejection } from '../../../../common/http/rejection';
import { messageSchema, type MessageResponse } from '../../core';
import type { RegistrationRequest } from '../model/registration';

/**
 * Creates an account. Never authenticates: the session and CSRF token are unchanged afterwards and the visitor logs
 * in separately. Failures propagate raw; the form maps them with toRegistrationRejection.
 */
export async function registerAccount(input: RegistrationRequest): Promise<MessageResponse> {
  return messageSchema.parse((await apiClient.post<MessageResponse>('/api/auth/register', input)).data);
}

/** A failed registration: the username or email is taken, or field errors, or one of the global kinds. */
export type RegistrationRejection = Rejection<'user-exists' | 'rejected'>;

export const toRegistrationRejection = (error: unknown): RegistrationRejection =>
  toRejection(error, ({ status, errors }) => {
    if (status !== 400) return undefined;
    return errors.some((entry) => entry.code === 'USER_EXISTS') ? 'user-exists' : 'rejected';
  });
