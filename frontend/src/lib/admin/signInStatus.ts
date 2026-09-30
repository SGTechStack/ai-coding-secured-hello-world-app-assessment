import { z } from 'zod'

/**
 * Why an account can or cannot sign in right now, apart from being enabled (ADR-075): its password lockout in each
 * lane, the NIST cap's disable, and its factor's two tiers. States and times only; each lock is present only while it
 * is in force.
 */
export const signInStatusSchema = z.object({
  accountLockedUntil: z.iso.datetime({ offset: true }).nullable(),
  failuresSinceSuccess: z.number().int().nonnegative(),
  lockedDevices: z.number().int().nonnegative(),
  nextDeviceUnlock: z.iso.datetime({ offset: true }).nullable(),
  capDisabledAt: z.iso.datetime({ offset: true }).nullable(),
  factorLockedUntil: z.iso.datetime({ offset: true }).nullable(),
  factorDisabled: z.boolean(),
})

export type SignInStatus = z.infer<typeof signInStatusSchema>

/**
 * Whether an admin unlock would clear anything: the account's lock, a trusted device's, or the factor's tier 1. The cap
 * and tier 2 are cleared only by rebinding (REJ-072). With no status to go on, unlock stays offered.
 */
export function isUnlockable(status: SignInStatus | undefined): boolean {
  return (
    status === undefined ||
    status.accountLockedUntil !== null ||
    status.lockedDevices > 0 ||
    status.factorLockedUntil !== null
  )
}

const time = (instant: string) => instant.replace('T', ' ').slice(0, 16) + ' UTC'

/** One line per restriction in force, most severe first; empty when the account can sign in. */
export function signInRestrictions(status: SignInStatus): string[] {
  const lines: string[] = []
  if (status.capDisabledAt !== null) {
    lines.push(
      `Password disabled by the failure cap since ${time(status.capDisabledAt)}; only a password reset clears it`,
    )
  }
  if (status.factorDisabled) {
    lines.push('Authenticator app disabled; only an authenticator app reset clears it')
  }
  if (status.accountLockedUntil !== null) {
    lines.push(`Locked for unrecognised devices until ${time(status.accountLockedUntil)}`)
  }
  if (status.lockedDevices > 0 && status.nextDeviceUnlock !== null) {
    lines.push(
      `${status.lockedDevices} trusted device${status.lockedDevices === 1 ? '' : 's'} locked, the first until ${time(status.nextDeviceUnlock)}`,
    )
  }
  if (status.factorLockedUntil !== null) {
    lines.push(`Authenticator app locked until ${time(status.factorLockedUntil)}`)
  }
  return lines
}

/** The list's compact badge: the most severe restriction's kind, or that the account can sign in. */
export function signInBadge(status: SignInStatus | undefined): string {
  if (status === undefined) {
    return '—'
  }
  if (status.capDisabledAt !== null || status.factorDisabled) {
    return 'Disabled by failures'
  }
  if (status.accountLockedUntil !== null || status.lockedDevices > 0 || status.factorLockedUntil !== null) {
    return 'Locked'
  }
  return 'Can sign in'
}
