import { apiFetch, refreshCsrfToken } from '@/lib/api/client'

/**
 * What `POST /api/mfa/totp/enrolment` returns, once (ADR-025): the `otpauth` URI, the same secret in Base32 for manual
 * entry, and the server-rendered QR code as a Base64 PNG. Never stored, cached or logged by the SPA.
 */
export interface Provisioning {
  otpauthUri: string
  secretBase32: string
  qrPng: string
}

/** Provisions a new secret, replacing any pending one. */
export const provisionTotp = () => apiFetch<Provisioning>('/api/mfa/totp/enrolment', { method: 'POST' })

/**
 * Confirms enrolment with a code from the authenticator app (enrolment binding, REJ-071). The server granted the factor
 * and rotated the session id, and with it the CSRF token, so the token is fetched again at once (ADR-040).
 */
export async function confirmTotp(code: string): Promise<void> {
  await apiFetch<void>('/api/mfa/totp/enrolment/confirmation', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ code }),
  })
  await refreshCsrfToken()
}

/** The QR code as an image `Blob`: Base64 → bytes → `image/png`. Shown through a `blob:` URL, never `data:` (ADR-060). */
export function qrPngBlob(base64: string): Blob {
  const bytes = Uint8Array.from(atob(base64), (char) => char.charCodeAt(0))
  return new Blob([bytes], { type: 'image/png' })
}

/** The Base32 secret in groups of four, easier to type into an authenticator. The spaces are cosmetic. */
export function groupSecret(secret: string): string {
  return secret.match(/.{1,4}/g)?.join(' ') ?? secret
}
