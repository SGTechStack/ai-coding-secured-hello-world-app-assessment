import type { z } from 'zod';

/**
 * Account rule codes the frontend handles; the frontend owns the text for each. Codes it only maps to a fallback
 * banner (e.g. `REQUEST_TOO_LARGE`, `FIELD_NOT_ALLOWED`) are not listed.
 */
export type AccountRuleCode = keyof typeof CODE_MESSAGES | 'FIELD_REQUIRED' | 'USER_EXISTS';

/** The Account fields whose rules the browser checks, as the API names them. */
export const ACCOUNT_FIELDS = ['username', 'email', 'password'] as const;
export type AccountField = (typeof ACCOUNT_FIELDS)[number];

const REQUIRED_MESSAGES: Record<AccountField, string> = {
  username: 'Username is required',
  email: 'Email is required',
  password: 'Password is required',
};

const CODE_MESSAGES = {
  USERNAME_TOO_SHORT: 'Username must be at least 5 characters',
  USERNAME_TOO_LONG: 'Username must be at most 100 characters',
  USERNAME_INVALID_CHARACTER:
    'Username may use only letters, digits, ".", "_" and "-", and must start with a letter or digit',
  EMAIL_INVALID: 'Enter a valid email address',
  EMAIL_TOO_LONG: 'Email must be at most 254 characters',
  PASSWORD_TOO_SHORT: 'Password must be at least 12 characters',
  PASSWORD_TOO_LONG: 'Password must be at most 72 characters',
  PASSWORD_INVALID_CHARACTER: 'Password may use only printable ASCII characters',
  PASSWORD_MISSING_UPPERCASE: 'Password must include an uppercase letter',
  PASSWORD_MISSING_LOWERCASE: 'Password must include a lowercase letter',
  PASSWORD_MISSING_DIGIT: 'Password must include a digit',
  PASSWORD_MISSING_SPECIAL: 'Password must include a special character',
  PASSWORD_CONTAINS_IDENTITY: 'Password must not contain your username or the name part of your email',
  PASSWORD_TOO_COMMON: 'This password is too common. Choose a different one',
} satisfies Record<string, string>;

/** Message for a field-level code, or undefined when the code is not shown on a field. */
export function fieldMessageFor(field: AccountField, code: string): string | undefined {
  return code === 'FIELD_REQUIRED' ? REQUIRED_MESSAGES[field] : CODE_MESSAGES[code as keyof typeof CODE_MESSAGES];
}

const PASSWORD_MIN = 12;
const PASSWORD_MAX = 72;
const USERNAME_PATTERN = /^[a-z0-9][a-z0-9._-]*$/;
const EMAIL_LOCAL_PART = /^[a-z0-9!#$%&'*+/=?^_`{|}~-]+(\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*$/;
const EMAIL_DOMAIN = /^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?(\.[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?)+$/;

/** Trims and lowercases an identifier, exactly as the backend does before lookup and storage. */
const normalizeIdentifier = (value: string) => value.trim().toLowerCase();

/** Password rules shared with the backend via `test-fixtures/password-policy-cases.json`. The password is never altered. */
export function passwordViolations(password: string, username: string, email: string): AccountRuleCode[] {
  if (password.length === 0) return ['FIELD_REQUIRED'];
  const chars = [...password];
  const codes: AccountRuleCode[] = [];
  if (chars.length < PASSWORD_MIN) codes.push('PASSWORD_TOO_SHORT');
  if (chars.length > PASSWORD_MAX) codes.push('PASSWORD_TOO_LONG');
  if (!/^[\x20-\x7E]*$/.test(password)) codes.push('PASSWORD_INVALID_CHARACTER');
  if (!/[A-Z]/.test(password)) codes.push('PASSWORD_MISSING_UPPERCASE');
  if (!/[a-z]/.test(password)) codes.push('PASSWORD_MISSING_LOWERCASE');
  if (!/\d/.test(password)) codes.push('PASSWORD_MISSING_DIGIT');
  // Printable ASCII that is not a letter or digit (space counts), matching the backend.
  if (!/[\x20-\x2F\x3A-\x40\x5B-\x60\x7B-\x7E]/.test(password)) codes.push('PASSWORD_MISSING_SPECIAL');
  if (containsIdentity(password.toLowerCase(), normalizeIdentifier(username), normalizeIdentifier(email))) {
    codes.push('PASSWORD_CONTAINS_IDENTITY');
  }
  return codes;
}

function containsIdentity(lowercasePassword: string, username: string, email: string): boolean {
  if (username.length >= 5 && lowercasePassword.includes(username)) return true;
  const at = email.lastIndexOf('@');
  const localPart = at < 0 ? '' : email.slice(0, at);
  return localPart.length >= 4 && lowercasePassword.includes(localPart);
}

export function usernameViolations(raw: string): AccountRuleCode[] {
  const username = normalizeIdentifier(raw);
  if (username.length === 0) return ['FIELD_REQUIRED'];
  const codes: AccountRuleCode[] = [];
  if (username.length < 5) codes.push('USERNAME_TOO_SHORT');
  if (username.length > 100) codes.push('USERNAME_TOO_LONG');
  if (!USERNAME_PATTERN.test(username)) codes.push('USERNAME_INVALID_CHARACTER');
  return codes;
}

export function emailViolations(raw: string): AccountRuleCode[] {
  const email = normalizeIdentifier(raw);
  if (email.length === 0) return ['FIELD_REQUIRED'];
  const codes: AccountRuleCode[] = [];
  if (email.length > 254) codes.push('EMAIL_TOO_LONG');
  const at = email.lastIndexOf('@');
  const valid =
    at > 0 &&
    at === email.indexOf('@') &&
    at < email.length - 1 &&
    email.slice(0, at).length <= 64 &&
    EMAIL_LOCAL_PART.test(email.slice(0, at)) &&
    EMAIL_DOMAIN.test(email.slice(at + 1));
  if (!valid) codes.push('EMAIL_INVALID');
  return codes;
}

/**
 * Live checklist rows: each frontend password rule, met or not. Without an identity (a Password reset, where the
 * browser does not know the account) the identity row is left out and only the server checks it.
 */
export function passwordChecklist(password: string, username?: string, email?: string) {
  const knowsIdentity = username !== undefined || email !== undefined;
  const violated = new Set(passwordViolations(password, username ?? '', email ?? ''));
  // A row is met once a password is entered and none of its codes is violated.
  return CHECKLIST.filter(([, ...codes]) => knowsIdentity || !codes.includes('PASSWORD_CONTAINS_IDENTITY')).map(
    ([label, ...codes]) => ({ label, met: password.length > 0 && !codes.some((code) => violated.has(code)) }),
  );
}

const CHECKLIST: [label: string, ...codes: AccountRuleCode[]][] = [
  ['12 to 72 characters', 'PASSWORD_TOO_SHORT', 'PASSWORD_TOO_LONG'],
  ['Only printable ASCII characters', 'PASSWORD_INVALID_CHARACTER'],
  ['An uppercase letter', 'PASSWORD_MISSING_UPPERCASE'],
  ['A lowercase letter', 'PASSWORD_MISSING_LOWERCASE'],
  ['A digit', 'PASSWORD_MISSING_DIGIT'],
  ['A special character', 'PASSWORD_MISSING_SPECIAL'],
  ['Does not contain your username or email name', 'PASSWORD_CONTAINS_IDENTITY'],
];

/** Reports every violated rule of `field` as one issue on `path`, one fixed message per line (deduplicated). */
export function reportViolations(
  context: z.RefinementCtx,
  path: string,
  field: AccountField,
  codes: AccountRuleCode[],
  fallback: string,
): void {
  if (codes.length === 0) return;
  const messages = codes.map((code) => fieldMessageFor(field, code) ?? fallback);
  context.addIssue({ code: 'custom', path: [path], message: [...new Set(messages)].join('\n') });
}

/** Checks the confirmation field, which never leaves the browser, against the password. */
export function checkConfirmation(context: z.RefinementCtx, password: string, confirmPassword: string): void {
  if (confirmPassword.length === 0) {
    context.addIssue({ code: 'custom', path: ['confirmPassword'], message: 'Confirm your password' });
  } else if (confirmPassword !== password) {
    context.addIssue({ code: 'custom', path: ['confirmPassword'], message: 'Passwords do not match' });
  }
}
