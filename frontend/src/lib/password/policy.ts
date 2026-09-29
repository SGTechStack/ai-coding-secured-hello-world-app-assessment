/**
 * The client's view of the password policy (ADR-005). The server is authoritative: this only checks the two length
 * rules before a request is sent, and names every rule the server can refuse with. Lengths are counted as the server
 * counts them, after NFC: code points for the minimum, UTF-8 bytes for the maximum (ADR-002; ADR-003).
 */

/** The rules, in the order the server runs them; a `PASSWORD_REJECTED` envelope's `rule` is one of these. */
export const PASSWORD_RULES = [
  'MIN_LENGTH',
  'MAX_BYTES',
  'BLOCKLISTED',
  'CONTEXT_TERM',
  'TOO_WEAK',
  'HISTORY_REUSE',
] as const

export type PasswordRule = (typeof PASSWORD_RULES)[number]

/** Mirrors `app.security.password.min-length`. */
export const MIN_LENGTH = 15
/** Mirrors `app.security.password.max-bytes`. */
export const MAX_BYTES = 72

/** What each rejection tells the user: one distinct, actionable message per rule (IM8 as-5; T-FE-018). */
export const RULE_COPY: Readonly<Record<PasswordRule, string>> = {
  MIN_LENGTH: `Use at least ${MIN_LENGTH} characters. A few unrelated words make a good passphrase.`,
  MAX_BYTES: `Use at most ${MAX_BYTES} bytes. Some characters, such as accented letters or emoji, take more than one.`,
  BLOCKLISTED: 'This password appears in known breaches or on our list of banned passwords. Choose another.',
  CONTEXT_TERM: 'Do not use your username, your email address or the name of this service in your password.',
  TOO_WEAK: 'This password is too easy to guess. Add more unrelated words, and avoid patterns and repeats.',
  HISTORY_REUSE: 'Choose a password you have not used for this account recently.',
}

const encoder = new TextEncoder()

export function isPasswordRule(value: unknown): value is PasswordRule {
  return typeof value === 'string' && (PASSWORD_RULES as readonly string[]).includes(value)
}

/** UTF-8 bytes after NFC, as the server counts them. */
export function utf8Bytes(password: string): number {
  return encoder.encode(password.normalize('NFC')).length
}

/** Code points after NFC, as the server counts them. */
export function codePoints(password: string): number {
  return [...password.normalize('NFC')].length
}

/** The length rule a password fails before it is sent, if any. The other rules need the server. */
export function lengthRule(password: string): PasswordRule | undefined {
  if (codePoints(password) < MIN_LENGTH) {
    return 'MIN_LENGTH'
  }
  if (utf8Bytes(password) > MAX_BYTES) {
    return 'MAX_BYTES'
  }
  return undefined
}
