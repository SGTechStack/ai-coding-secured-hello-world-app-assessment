import { z } from 'zod';

const time = z.string().nullable();

/** One Account in the User list: everything stored about it except the password hash. Times are UTC ISO-8601. */
export const listedAccountSchema = z.object({
  id: z.string().min(1),
  username: z.string(),
  email: z.string().nullable(),
  role: z.string(),
  enabled: z.boolean(),
  deleted: z.boolean(),
  locked: z.boolean(),
  failedLoginAttempts: z.number(),
  lockedUntil: time,
  disabledAt: time,
  deletedAt: time,
  lastLoginAt: time,
  createdAt: z.string(),
  updatedAt: z.string(),
});
export type ListedAccount = z.infer<typeof listedAccountSchema>;

/** `GET /api/admin/users`: Spring Data's page JSON; `number` is 0-based. */
export const userListPageSchema = z.object({
  content: z.array(listedAccountSchema),
  page: z.object({ size: z.number(), number: z.number(), totalElements: z.number(), totalPages: z.number() }),
});
export type UserListPage = z.infer<typeof userListPageSchema>;

/** The server's default page size; the client never sends another. */
export const PAGE_SIZE = 20;

export type AccountStatus = 'Deleted' | 'Disabled' | 'Locked' | 'Active';

/** Display only: the three booleans are the state, and a deleted Account is also disabled. */
export function accountStatus(account: Pick<ListedAccount, 'deleted' | 'enabled' | 'locked'>): AccountStatus {
  if (account.deleted) return 'Deleted';
  if (!account.enabled) return 'Disabled';
  return account.locked ? 'Locked' : 'Active';
}

/** The one display rule for a role: "User", "Admin", else its raw name. */
export function roleLabel(role: string) {
  if (role === 'USER') return 'User';
  return role === 'ADMIN' ? 'Admin' : role;
}

const SGT = new Intl.DateTimeFormat('en-SG', { timeZone: 'Asia/Singapore', dateStyle: 'medium', timeStyle: 'short' });

/** A UTC instant in Singapore time, e.g. "29 Sep 2026, 9:14 am". */
export const formatSgt = (iso: string) => SGT.format(new Date(iso));
