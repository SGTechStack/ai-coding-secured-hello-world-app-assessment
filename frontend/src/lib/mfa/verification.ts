import { apiFetch, refreshCsrfToken } from '@/lib/api/client'

/**
 * Verifies a code from the authenticator app, `POST /api/mfa/totp/verification` (ADR-021). The code travels in the
 * JSON body (REJ-070). The server granted the factor and rotated the session id, and with it the CSRF token, so the
 * token is fetched again at once (ADR-040).
 */
export async function verifyTotp(code: string): Promise<void> {
  await apiFetch<void>('/api/mfa/totp/verification', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ code }),
  })
  await refreshCsrfToken()
}
