import { z } from 'zod';

/** `GET /api/profile`: the caller's own id and role, used to restore the Session after a reload. */
export const profileSchema = z.object({ id: z.string().min(1), role: z.string().min(1) });
export type Profile = z.infer<typeof profileSchema>;
