/**
 * Client-side mirrors of the backend's input limits (spec "Input limits").
 * The server remains authoritative; these only save a round-trip and give
 * precise inline feedback.
 */

export const USERNAME_PATTERN = /^[A-Za-z0-9._-]{3,64}$/;
// Deliberately simple: one "@", no whitespace, a dot in the domain part.
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export const LIMITS = {
  usernameMax: 64,
  emailMax: 254,
  firstNameMax: 100,
  passwordMin: 12,
  passwordMaxBytes: 72,
  // Input `maxLength` for password fields: generous so multi-byte passwords
  // aren't cut off mid-typing; the UTF-8 byte check is the real limit.
  passwordInputMax: 128,
} as const;

export function utf8ByteLength(value: string): number {
  return new TextEncoder().encode(value).length;
}

export function validateUsername(username: string): string | undefined {
  if (!username.trim()) {
    return 'Username is required';
  }
  if (!USERNAME_PATTERN.test(username)) {
    return 'Username must be 3-64 characters: letters, numbers, dots, underscores or hyphens';
  }
  return undefined;
}

export function validateEmail(email: string): string | undefined {
  if (!email.trim()) {
    return 'Email is required';
  }
  if (email.length > LIMITS.emailMax || !EMAIL_PATTERN.test(email)) {
    return 'Enter a valid email address';
  }
  return undefined;
}

export function validateFirstName(firstName: string): string | undefined {
  if (!firstName.trim()) {
    return 'First name is required';
  }
  if (firstName.length > LIMITS.firstNameMax) {
    return `First name must be at most ${LIMITS.firstNameMax} characters`;
  }
  return undefined;
}

export function validateNewPassword(password: string): string | undefined {
  if (!password) {
    return 'Password is required';
  }
  if (password.length < LIMITS.passwordMin) {
    return `Password must be at least ${LIMITS.passwordMin} characters long`;
  }
  if (utf8ByteLength(password) > LIMITS.passwordMaxBytes) {
    return 'Password is too long (maximum 72 bytes; some characters count as more than one)';
  }
  return undefined;
}
