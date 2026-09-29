/**
 * Client-side mirror of the backend PasswordPolicy (Java).
 * The backend is authoritative — this is UX convenience only.
 * Policy: length 12-72 AND characters from at least 3 of 4 categories.
 */

export const MIN_PASSWORD = 12;
export const MAX_PASSWORD = 72;  // BCrypt byte cap
const MIN_CATEGORIES = 3;

export interface PasswordCategory {
  label: string;
  met: boolean;
}

/** Returns the first policy violation message (length before complexity), or null if valid. */
export function validatePassword(value: string): string | null {
  if (!value) return 'Password is required.';
  if (value.length < MIN_PASSWORD) return `Password must be at least ${MIN_PASSWORD} characters.`;
  if (value.length > MAX_PASSWORD) return `Password must not exceed ${MAX_PASSWORD} characters.`;

  const categories = countCategories(value);
  if (categories < MIN_CATEGORIES) {
    return `Password must include characters from at least ${MIN_CATEGORIES} of the following: `
      + 'uppercase letters, lowercase letters, numbers, special characters.';
  }
  return null;
}

/** Returns 0-4: how many of the four character categories are present. */
function countCategories(value: string): number {
  return [
    /[A-Z]/.test(value),       // uppercase
    /[a-z]/.test(value),       // lowercase
    /[0-9]/.test(value),       // digit
    /[^A-Za-z0-9]/.test(value), // special
  ].filter(Boolean).length;
}

/** Builds the requirements checklist for the PasswordField component. */
export function buildRequirements(value: string): PasswordCategory[] {
  return [
    { label: `At least ${MIN_PASSWORD} characters`, met: value.length >= MIN_PASSWORD },
    { label: `No more than ${MAX_PASSWORD} characters`, met: value.length <= MAX_PASSWORD && value.length > 0 },
    { label: 'Uppercase letter (A–Z)', met: /[A-Z]/.test(value) },
    { label: 'Lowercase letter (a–z)', met: /[a-z]/.test(value) },
    { label: 'Number (0–9)', met: /[0-9]/.test(value) },
    { label: 'Special character', met: /[^A-Za-z0-9]/.test(value) },
    {
      label: `At least ${MIN_CATEGORIES} character types`,
      met: countCategories(value) >= MIN_CATEGORIES,
    },
  ];
}
