import { z } from 'zod';

/** UX-only checks for the sign-in form; the server decides whether the credentials are valid. */
export const signInSchema = z.object({
  username: z.string().trim().min(1, 'Enter your username'),
  password: z.string().min(1, 'Enter your password'),
});

export type SignInValues = z.infer<typeof signInSchema>;
