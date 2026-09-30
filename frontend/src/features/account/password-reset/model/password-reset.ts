import { z } from 'zod';
import { fieldFeedbackFor, INFRASTRUCTURE_TEXT, TOO_MANY_REQUESTS_TEXT } from '../../../../common/http/rejection';
import type { PasswordResetRejection } from '../api/password-reset-api';
import { checkConfirmation, emailViolations, fieldMessageFor, passwordViolations, reportViolations } from '../../core';

/** Wire request of `POST /api/auth/password-reset`. */
export type PasswordResetRequest = { email: string };

export type ForgotPasswordBanner = 'requested' | 'too-many-requests' | 'fallback' | 'infrastructure';
/** Fixed copy. `requested` is shown for every accepted request, whether or not the email is registered (ADR 0004). */
export const FORGOT_PASSWORD_BANNER_TEXT: Record<ForgotPasswordBanner, string> = {
  requested:
    'If an account is registered with that email, we have sent a link to reset its password. ' +
    'The link expires in 30 minutes.',
  'too-many-requests': TOO_MANY_REQUESTS_TEXT,
  fallback: 'Unable to request a reset with these details.',
  infrastructure: INFRASTRUCTURE_TEXT,
};

/** Maps a failed request to fixed feedback: a banner, or messages for the email field. Never response content. */
export function forgotPasswordFeedbackFor(rejection: PasswordResetRejection): {
  banner?: ForgotPasswordBanner;
  emailMessages: string[];
} {
  if (rejection.kind === 'too-many-attempts') return { banner: 'too-many-requests', emailMessages: [] };
  if (rejection.kind !== 'rejected') return { banner: 'infrastructure', emailMessages: [] };
  const { fallback, fieldMessages } = fieldFeedbackFor<'email'>(rejection.errors, ({ field, code }) =>
    field === 'email' ? ['email', fieldMessageFor('email', code)] : undefined,
  );
  const emailMessages = fieldMessages.email ?? [];
  return fallback ? { banner: 'fallback', emailMessages } : { emailMessages };
}

/** Client-side form schema: the same email rules as registration, checked before any request. */
export const forgotPasswordSchema = z.object({ email: z.string() }).superRefine((input, context) => {
  reportViolations(context, 'email', 'email', emailViolations(input.email), FORGOT_PASSWORD_BANNER_TEXT.fallback);
});
export type ForgotPasswordInput = z.infer<typeof forgotPasswordSchema>;

/** Wire request of `POST /api/auth/password-reset/confirm`. */
export type PasswordResetConfirmRequest = { token: string; newPassword: string };

export type ResetPasswordBanner = 'too-many-requests' | 'fallback' | 'infrastructure';
export const RESET_PASSWORD_BANNER_TEXT: Record<ResetPasswordBanner, string> = {
  'too-many-requests': TOO_MANY_REQUESTS_TEXT,
  fallback: 'Unable to reset your password with these details.',
  infrastructure: INFRASTRUCTURE_TEXT,
};
/** Password history rejection: the current password or one of the recent ones. */
const PASSWORD_REUSED_TEXT = 'You used this password recently. Choose a different one';
export const INVALID_RESET_LINK_TEXT = 'This reset link is invalid or has expired.';
export const PASSWORD_UPDATED_TEXT = 'Password updated. Log in with your new password.';

/** How a failed confirm is shown: the link is dead, a banner, or messages on the new-password field. */
type ResetPasswordFeedback = { invalidLink?: true; banner?: ResetPasswordBanner; passwordMessages: string[] };

export function resetPasswordFeedbackFor(rejection: PasswordResetRejection): ResetPasswordFeedback {
  if (rejection.kind === 'invalid-reset-link') return { invalidLink: true, passwordMessages: [] };
  if (rejection.kind === 'too-many-attempts') return { banner: 'too-many-requests', passwordMessages: [] };
  if (rejection.kind !== 'rejected') return { banner: 'infrastructure', passwordMessages: [] };
  const { fallback, fieldMessages } = fieldFeedbackFor<'newPassword'>(rejection.errors, ({ field, code }) =>
    field === 'newPassword'
      ? ['newPassword', code === 'PASSWORD_REUSED' ? PASSWORD_REUSED_TEXT : fieldMessageFor('password', code)]
      : undefined,
  );
  const passwordMessages = fieldMessages.newPassword ?? [];
  return fallback ? { banner: 'fallback', passwordMessages } : { passwordMessages };
}

/**
 * Client-side form schema: the registration password rules the browser can check without knowing the account, and
 * the confirmation, which never leaves the browser. The server also checks identity and the common-password list.
 */
export const resetPasswordSchema = z
  .object({ newPassword: z.string(), confirmPassword: z.string() })
  .superRefine((input, context) => {
    reportViolations(
      context,
      'newPassword',
      'password',
      passwordViolations(input.newPassword, '', ''),
      RESET_PASSWORD_BANNER_TEXT.fallback,
    );
    checkConfirmation(context, input.newPassword, input.confirmPassword);
  });
export type ResetPasswordInput = z.infer<typeof resetPasswordSchema>;
