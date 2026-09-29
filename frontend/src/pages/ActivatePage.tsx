import { type LinkPasswordCopy, LinkPasswordForm } from '@/components/LinkPasswordForm'
import { activate } from '@/lib/auth/registration'

export const INVALID_LINK =
  'This activation link is not valid. It may have expired, been used already or been replaced by a newer one. Register again to get a new link.'

const COPY: LinkPasswordCopy = {
  heading: 'Activate your account',
  submitLabel: 'Activate',
  doneHeading: 'Account activated',
  invalidLink: INVALID_LINK,
  newLink: { to: '/register', label: 'Register' },
  genericFailure: 'Activation failed. Try again.',
}

/** Self-registration, step two (ADR-032): the activation link sets the first password here. */
export function ActivatePage() {
  return <LinkPasswordForm copy={COPY} submit={activate} />
}
