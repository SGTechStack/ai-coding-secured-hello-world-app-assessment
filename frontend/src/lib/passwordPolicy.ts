/** Client-side mirror of the server's PasswordPolicy, for immediate feedback only. */
export const MIN_PASSWORD_LENGTH = 12;
export const MAX_PASSWORD_LENGTH = 72;

export function passwordProblems(password: string, username?: string): string[] {
  const problems: string[] = [];
  if (password.length < MIN_PASSWORD_LENGTH) {
    problems.push(`Must be at least ${MIN_PASSWORD_LENGTH} characters long`);
    return problems;
  }
  if (password.length > MAX_PASSWORD_LENGTH) {
    problems.push(`Must be at most ${MAX_PASSWORD_LENGTH} characters long`);
  }
  if (username && username.trim() && password.toLowerCase().includes(username.trim().toLowerCase())) {
    problems.push('Must not contain the username');
  }
  return problems;
}
