import { type LinkPasswordCopy, LinkPasswordForm } from '@/components/LinkPasswordForm'
import { confirmPasswordReset } from '@/lib/auth/passwordReset'

export const INVALID_RESET_LINK =
  'This reset link is not valid. It may have expired, been used already or been replaced by a newer one. Request a new link.'

const COPY: LinkPasswordCopy = {
  heading: 'Reset your password',
  submitLabel: 'Reset password',
  doneHeading: 'Password reset',
  invalidLink: INVALID_RESET_LINK,
  newLink: { to: '/forgot-password', label: 'Request a new link' },
  genericFailure: 'The reset failed. Try again.',
}

/**
 * Password reset, step two (PRD Story 7): the emailed link, or a token an administrator issued, sets a new password
 * here. Every session of the account ends, so the user signs in again next.
 */
export function ResetPasswordPage() {
  return <LinkPasswordForm copy={COPY} submit={confirmPasswordReset} />
}
