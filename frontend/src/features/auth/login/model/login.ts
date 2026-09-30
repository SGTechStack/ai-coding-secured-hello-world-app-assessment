import { z } from 'zod';

export const loginSchema = z.object({
  username: z
    .string()
    .min(1, 'Username is required')
    .min(5, 'Username must be at least 5 characters')
    .regex(/^[^/\\]*$/, 'Username must not contain / or \\'),
  password: z.string().min(1, 'Password is required'),
});
export type LoginInput = z.infer<typeof loginSchema>;

/** `POST /api/auth/login`'s body: the new Session's profile. A body without an id or role breaks the contract. */
export const loginResponseSchema = z.object({
  profile: z.object({ id: z.string().min(1), username: z.string().min(1), role: z.string().min(1) }),
});
export type LoginResponse = z.infer<typeof loginResponseSchema>;
