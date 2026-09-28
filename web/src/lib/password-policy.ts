/**
 * The password rules, mirrored from the server for immediate feedback.
 *
 * A mirror, not the policy. The server validates independently and its answer is the one that counts
 * — this only exists so someone does not have to submit a form to learn their password is too short.
 * If the two ever disagree, the server is right and this file is stale.
 */
export const MIN_PASSWORD_LENGTH = 12;
export const MAX_PASSWORD_BYTES = 72;

export const PASSWORD_HINT = `At least ${MIN_PASSWORD_LENGTH} characters. Length is the whole rule — no symbols required.`;

export function describePasswordProblem(password: string): string | undefined {
  if (password.length === 0) {
    return "Enter a password.";
  }
  if (password.length < MIN_PASSWORD_LENGTH) {
    return `Use at least ${MIN_PASSWORD_LENGTH} characters.`;
  }
  // Bytes, not characters: the server's limit is BCrypt's 72-byte input, which accented letters and
  // emoji reach well before 72 keystrokes.
  if (new TextEncoder().encode(password).length > MAX_PASSWORD_BYTES) {
    return `That is too long. Keep it under ${MAX_PASSWORD_BYTES} bytes.`;
  }
  return undefined;
}
