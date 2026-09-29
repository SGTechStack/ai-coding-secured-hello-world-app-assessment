import { apiFetch } from '@/lib/api/client'

/**
 * Two-step self-registration (ADR-032). Step one reserves a username for an email address and answers the same 202
 * whether or not the address is in use; the activation link goes to the address. Neither step signs anyone in.
 */

/** `POST /api/register`. Resolves on the uniform 202; a taken username is `VALIDATION_FAILED` with a `rule`. */
export async function register(username: string, email: string): Promise<void> {
  await apiFetch<void>('/api/register', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, email }),
  })
}

/** `POST /api/register/activate`: redeems the activation token and sets the first password. */
export async function activate(token: string, password: string): Promise<void> {
  await apiFetch<void>('/api/register/activate', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ token, password }),
  })
}

/** The token an activation or reset link carries in its fragment (`#token=...`), or `''` if there is none. */
export function tokenFromFragment(hash: string): string {
  return new URLSearchParams(hash.replace(/^#/, '')).get('token') ?? ''
}
