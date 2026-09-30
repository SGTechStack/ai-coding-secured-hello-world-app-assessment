import { z } from 'zod';
import { fieldFeedbackFor, INFRASTRUCTURE_TEXT } from '../../../../common/http/rejection';
import {
  ACCOUNT_FIELDS,
  checkConfirmation,
  emailViolations,
  fieldMessageFor,
  passwordViolations,
  reportViolations,
  usernameViolations,
  type AccountField,
} from '../../core';
import type { RegistrationRejection } from '../api/registration-api';

export type RegistrationField = AccountField;

/** Wire request: exactly the three fields the API accepts. The confirmation never leaves the browser. */
export type RegistrationRequest = Omit<RegistrationInput, 'confirmPassword'>;

export type RegistrationBanner = 'user-exists' | 'fallback' | 'too-many-attempts' | 'infrastructure';
export const REGISTRATION_BANNER_TEXT: Record<RegistrationBanner, string> = {
  'user-exists': 'Username or email is already in use.',
  fallback: 'Unable to create account with these details.',
  'too-many-attempts': 'Registration is temporarily unavailable. Please try again later.',
  infrastructure: INFRASTRUCTURE_TEXT,
};

/** Maps a rejected registration to fixed feedback: an optional banner plus messages per field. */
export function registrationFeedbackFor(rejection: RegistrationRejection): {
  banner?: RegistrationBanner;
  fieldMessages: Partial<Record<RegistrationField, string[]>>;
} {
  if (rejection.kind === 'too-many-attempts') return { banner: 'too-many-attempts', fieldMessages: {} };
  if (rejection.kind === 'user-exists') return { banner: 'user-exists', fieldMessages: {} };
  if (rejection.kind !== 'rejected') return { banner: 'infrastructure', fieldMessages: {} };
  const { fallback, fieldMessages } = fieldFeedbackFor<RegistrationField>(rejection.errors, ({ field, code }) => {
    const known = ACCOUNT_FIELDS.find((candidate) => candidate === field);
    return known && [known, fieldMessageFor(known, code)];
  });
  return { banner: fallback ? 'fallback' : undefined, fieldMessages };
}

/** Client-side form schema. Confirmation is checked here and never sent to the API. */
export const registrationSchema = z
  .object({
    username: z.string(),
    email: z.string(),
    password: z.string(),
    confirmPassword: z.string(),
  })
  .superRefine((input, context) => {
    const fallback = REGISTRATION_BANNER_TEXT.fallback;
    reportViolations(context, 'username', 'username', usernameViolations(input.username), fallback);
    reportViolations(context, 'email', 'email', emailViolations(input.email), fallback);
    reportViolations(
      context,
      'password',
      'password',
      passwordViolations(input.password, input.username, input.email),
      fallback,
    );
    checkConfirmation(context, input.password, input.confirmPassword);
  });
export type RegistrationInput = z.infer<typeof registrationSchema>;
