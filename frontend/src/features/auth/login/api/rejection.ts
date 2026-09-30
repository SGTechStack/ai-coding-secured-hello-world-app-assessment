import { toRejection, type Rejection } from '../../../../common/http/rejection';

/** A failed Login attempt: an Authentication failure (401), or one of the global kinds. */
export type LoginRejection = Rejection<'invalid-credentials'>;

export const toLoginRejection = (error: unknown): LoginRejection =>
  toRejection(error, ({ status }) => (status === 401 ? 'invalid-credentials' : undefined));
