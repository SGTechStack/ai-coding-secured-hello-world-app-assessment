import { z } from 'zod';

// Wire response bodies shared by the Account use cases.

/** A `{ message }` success body (registration, Password reset request). Its text is never shown. */
export const messageSchema = z.object({ message: z.string() });
export type MessageResponse = z.infer<typeof messageSchema>;
