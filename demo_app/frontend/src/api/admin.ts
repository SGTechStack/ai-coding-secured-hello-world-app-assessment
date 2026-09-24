import { queryOptions } from "@tanstack/react-query";
import type { Role } from "./auth";
import { apiRequest } from "./client";

/**
 * One account as the admin API lists it. The API never sends the password hash, lockout state or
 * reset tokens, so neither can this type.
 */
export type AdminUser = {
  id: number;
  username: string;
  email: string;
  firstName: string;
  role: Role;
  enabled: boolean;
  /** ISO-8601 instant. */
  createdAt: string;
};

/** Every account, oldest first. Admin only: the API answers `403` to anyone else. */
export function listUsers(): Promise<AdminUser[]> {
  return apiRequest<AdminUser[]>("/api/v1/admin/users");
}

/** The admin user list. Admin actions invalidate it (`adminUsersQueryOptions.queryKey`). */
export const adminUsersQueryOptions = queryOptions({
  queryKey: ["admin", "users"],
  queryFn: listUsers,
});
