import { queryOptions } from "@tanstack/react-query";
import { ApiError, apiRequest, dropCsrfToken } from "./client";

export type Credentials = { username: string; password: string };

/** Public profile of the signed-in user, as returned by the auth API. */
export type UserProfile = { username: string; firstName: string };

/**
 * Logs in. A 403 (a stale CSRF token) is retried once with a freshly issued token; if that fails
 * too, the error propagates. The server rotates the token on login, so the old one is dropped.
 */
export async function login(credentials: Credentials): Promise<UserProfile> {
  const profile = await withCsrfRetry(() => postLogin(credentials));
  dropCsrfToken();
  return profile;
}

function postLogin(credentials: Credentials): Promise<UserProfile> {
  return apiRequest<UserProfile>("/api/v1/auth/login", {
    method: "POST",
    body: credentials,
  });
}

/**
 * Ends the session on the server, which answers an empty `204`. A 401 means there is no session
 * left to end, so it counts as success. A 403 is retried once with a fresh CSRF token, like
 * `login()`; anything else, or a failed retry, propagates. The server clears the token on logout,
 * so the old one is dropped.
 */
export async function logout(): Promise<void> {
  try {
    await withCsrfRetry(postLogout);
  } catch (error) {
    if (!(error instanceof ApiError && error.kind === "unauthorized"))
      throw error;
  }
  dropCsrfToken();
}

function postLogout(): Promise<void> {
  return apiRequest<void>("/api/v1/auth/logout", { method: "POST" });
}

/**
 * Runs `request`; on a `403` (a missing or stale CSRF token), drops the token and runs it once
 * more, which fetches a fresh one. Any other error, e.g. a `400`, `409` or `429`, is not retried:
 * resending would not change the answer.
 */
async function withCsrfRetry<T>(request: () => Promise<T>): Promise<T> {
  try {
    return await request();
  } catch (error) {
    if (!(error instanceof ApiError && error.status === 403)) throw error;
    dropCsrfToken();
    return request();
  }
}

export function fetchMe(): Promise<UserProfile> {
  return apiRequest<UserProfile>("/api/v1/auth/me");
}

/**
 * The session's profile, rehydrated from the server (`GET /me`) on a fresh load and seeded by a
 * successful login. It only changes when the session does, so it never goes stale on its own.
 */
export const meQueryOptions = queryOptions({
  queryKey: ["me"],
  queryFn: fetchMe,
  staleTime: Infinity,
});
