import { apiFetch } from '@/lib/api/client'

/** One seeded demo account, as `GET /api/dev/demo-accounts` lists it. */
export interface DemoAccount {
  username: string
  role: 'USER' | 'ADMIN'
  /**
   * Whether the account exists as seeded. False when it was deleted or its username is held by an account the seeder
   * did not create (a database from before the demo accounts); it then carries no email, password or code.
   */
  seeded: boolean
  /** The seeded email address, for the forgot-password flow, or null when not seeded. */
  email: string | null
  /** The committed password, or null once it has been changed. */
  password: string | null
  passwordChanged: boolean
  /** The administrator's current TOTP code, or null. */
  totp: { code: string; secondsRemaining: number } | null
}

/** The query key of the demo accounts. */
export const DEMO_ACCOUNTS_KEY = ['demo-accounts'] as const

/**
 * The dev-only demo accounts, or an empty list whenever the server does not answer 200 with some. The endpoint exists
 * only under the backend's `dev` profile; everywhere else it is refused like any unknown route, and the sign-in page
 * then shows nothing. No demo value is in this bundle: every one comes from this call.
 */
export async function fetchDemoAccounts(): Promise<DemoAccount[]> {
  try {
    const body = await apiFetch<{ accounts?: DemoAccount[] } | undefined>('/api/dev/demo-accounts')
    return Array.isArray(body?.accounts) ? body.accounts : []
  } catch {
    return []
  }
}

/** When to fetch again: just after the administrator's code expires, or never if no code is shown. */
export function refreshAfterMs(accounts: DemoAccount[] | undefined): number | false {
  const seconds = accounts?.find((account) => account.totp)?.totp?.secondsRemaining
  return seconds === undefined ? false : seconds * 1000 + 250
}
