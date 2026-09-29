import type { FieldError } from './Field'

/** Messages for the Credential policy rules the API names in `violations`. */
const PASSWORD_RULES: Record<string, string> = {
  min_length: 'Use at least 12 characters.',
  max_length: 'Use at most 64 characters.',
  max_bytes: 'Use at most 72 bytes. Accented letters and symbols take more than one byte each.',
  uppercase: 'Include an uppercase letter.',
  lowercase: 'Include a lowercase letter.',
  digit: 'Include a digit.',
  special: 'Include a special character: anything that is not a letter or digit, such as a space or !.',
  common_password: 'Choose a less common password.',
}

/** One field error per broken Credential policy rule; an unknown rule gets a generic message. */
export function passwordRuleErrors(violations: string[]): FieldError[] {
  return violations.map((rule) => ({ key: rule, message: PASSWORD_RULES[rule] ?? 'The password is not allowed.' }))
}
