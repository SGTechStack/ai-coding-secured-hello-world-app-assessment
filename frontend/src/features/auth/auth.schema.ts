import { z } from 'zod';

/** UX-only checks for the sign-in form; the server decides whether the credentials are valid. */
export const signInSchema = z.object({
  username: z.string().trim().min(1, 'Enter your username'),
  password: z.string().min(1, 'Enter your password'),
});

export type SignInValues = z.infer<typeof signInSchema>;

/** Matches the server's password policy: at least this many characters. */
export const MIN_PASSWORD_LENGTH = 12;

/** UX-only checks for the change-password form; the server enforces the same rules. */
export const changePasswordSchema = z
  .object({
    newPassword: z.string().min(MIN_PASSWORD_LENGTH, `Use at least ${MIN_PASSWORD_LENGTH} characters`),
    confirmPassword: z.string().min(1, 'Type the new password again'),
  })
  .refine((values) => values.newPassword === values.confirmPassword, {
    message: 'The two passwords do not match',
    path: ['confirmPassword'],
  });

export type ChangePasswordValues = z.infer<typeof changePasswordSchema>;
