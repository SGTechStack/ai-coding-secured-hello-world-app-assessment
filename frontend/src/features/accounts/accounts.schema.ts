import { z } from 'zod';
import { ACCOUNT_ROLES } from './accounts.queries';

/** Matches the server's username column length; the server has the final say. */
const USERNAME_MAX_LENGTH = 100;

/** UX-only checks for the create-account form; the server decides uniqueness and validity. */
export const createAccountSchema = z.object({
  username: z
    .string()
    .trim()
    .min(1, 'Enter a username')
    .max(USERNAME_MAX_LENGTH, `Use at most ${USERNAME_MAX_LENGTH} characters`),
  role: z.enum(ACCOUNT_ROLES),
});

export type CreateAccountValues = z.infer<typeof createAccountSchema>;
