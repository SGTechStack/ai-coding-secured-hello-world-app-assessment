/**
 * The password length the API enforces (spec: 12 to 64 characters), checked before submitting so
 * most mistakes don't need a round trip. The API remains the authority, e.g. for the 72-byte
 * limit and the common-password list.
 */
export const PASSWORD_MIN_LENGTH = 12;
export const PASSWORD_MAX_LENGTH = 64;

/** The helper text under a new-password form's title. */
export const PASSWORD_HINT = `a password of at least ${PASSWORD_MIN_LENGTH} characters`;

/** Why `password` can't be submitted, or `undefined` if its length is acceptable. */
export function passwordProblem(password: string): string | undefined {
  if (!password) return "Password is required";
  // Characters, not UTF-16 units, as the API counts them.
  const length = [...password].length;
  if (length < PASSWORD_MIN_LENGTH || length > PASSWORD_MAX_LENGTH)
    return `Password must be ${PASSWORD_MIN_LENGTH} to ${PASSWORD_MAX_LENGTH} characters.`;
  return undefined;
}
