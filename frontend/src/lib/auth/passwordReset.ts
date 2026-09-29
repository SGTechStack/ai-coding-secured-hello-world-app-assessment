import { apiFetch } from '@/lib/api/client'

/**
 * Password reset (PRD Stories 6 and 7). The request answers the same 202 whether or not the address has an account;
 * the link goes to the address. Redemption signs nobody in and ends every session of the account.
 */

/** `POST /api/password-reset/request`. Resolves on the uniform 202. */
export async function requestPasswordReset(email: string): Promise<void> {
  await apiFetch<void>('/api/password-reset/request', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email }),
  })
}

/** `POST /api/password-reset/confirm`: redeems a reset token, emailed or issued by an administrator. */
export async function confirmPasswordReset(token: string, password: string): Promise<void> {
  await apiFetch<void>('/api/password-reset/confirm', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ token, password }),
  })
}
