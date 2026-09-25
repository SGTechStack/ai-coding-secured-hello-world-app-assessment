import {
  queryOptions,
  useMutation,
  useQueryClient,
} from "@tanstack/react-query";
import type { Role } from "./auth";
import { apiRequest, withCsrfRetry } from "./client";

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

/**
 * Disables (`enabled: false`) or re-enables account `id`; resolves to the updated row. Like every
 * admin action, a 403 (a stale CSRF token) is retried once with a fresh token.
 */
export function setUserEnabled(
  id: number,
  enabled: boolean,
): Promise<AdminUser> {
  return withCsrfRetry(() =>
    apiRequest<AdminUser>(`/api/v1/admin/users/${id}/status`, {
      method: "PATCH",
      body: { enabled },
    }),
  );
}

/** Gives account `id` the `role`; resolves to the updated row. */
export function setUserRole(id: number, role: Role): Promise<AdminUser> {
  return withCsrfRetry(() =>
    apiRequest<AdminUser>(`/api/v1/admin/users/${id}/role`, {
      method: "PATCH",
      body: { role },
    }),
  );
}

/** Deletes account `id` (the API answers an empty `204`). */
export function deleteUser(id: number): Promise<void> {
  return withCsrfRetry(() =>
    apiRequest<void>(`/api/v1/admin/users/${id}`, { method: "DELETE" }),
  );
}

/**
 * A mutation that runs one admin action (e.g. `() => setUserEnabled(id, false)`) and, once it
 * succeeds, refetches the user list, so the table always shows what the server now holds. The
 * API refuses actions on the caller's own account, whatever the UI does.
 */
export function useAdminUserAction() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (action: () => Promise<unknown>) => action(),
    onSuccess: () =>
      queryClient.invalidateQueries({
        queryKey: adminUsersQueryOptions.queryKey,
      }),
  });
}
