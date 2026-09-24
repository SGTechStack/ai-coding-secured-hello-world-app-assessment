import { http, HttpResponse } from "msw";
import type { AdminUser } from "../api/admin";
import type { UserProfile } from "../api/auth";
import { API_BASE_URL } from "../api/client";

export const demoUser: UserProfile = {
  username: "johndoe",
  firstName: "John",
  role: "USER",
};
export const csrfToken = "test-xsrf-token";

/** The bootstrap admin's profile, as `/me` reports it. */
export const adminProfile: UserProfile = {
  username: "admin",
  firstName: "Admin",
  role: "ADMIN",
};

/** The admin user list the default handler returns, oldest first. */
export const adminUsers: AdminUser[] = [
  {
    id: 1,
    username: "johndoe",
    email: "johndoe@example.com",
    firstName: "John",
    role: "USER",
    enabled: true,
    createdAt: "2026-01-15T09:30:00Z",
  },
  {
    id: 2,
    username: "admin",
    email: "admin@example.com",
    firstName: "Admin",
    role: "ADMIN",
    enabled: true,
    createdAt: "2026-01-16T10:00:00Z",
  },
];

/**
 * The absolute URL of an API path. The API is cross-origin (jsdom runs on localhost:3000), so
 * handlers must match the client's base URL; a relative path would never match.
 */
export function api(path: string): string {
  return `${API_BASE_URL}${path}`;
}

/**
 * The API's `GET /api/v1/auth/csrf` reply: the token in the body. The real API also sets the
 * `XSRF-TOKEN` cookie, but the SPA cannot read it cross-origin, so the mock leaves it out to prove
 * the client uses the body.
 */
export function csrfResponse() {
  return HttpResponse.json({ headerName: "X-XSRF-TOKEN", token: csrfToken });
}

/** Happy-path API. Tests override per case with `server.use(...)`. */
export const handlers = [
  http.get(api("/api/v1/auth/csrf"), () => csrfResponse()),
  http.post(api("/api/v1/auth/login"), () => HttpResponse.json(demoUser)),
  // Registration succeeds with the profile of whoever registered (the API creates no session).
  http.post(api("/api/v1/auth/register"), async ({ request }) => {
    const body = (await request.json()) as Record<string, string>;
    return HttpResponse.json(
      {
        username: body.username?.toLowerCase(),
        firstName: body.firstName,
        role: "USER",
      } satisfies Partial<UserProfile>,
      { status: 201 },
    );
  }),
  // Reset request: always an empty 202. Confirm: an empty 204.
  http.post(
    api("/api/v1/auth/password-reset/request"),
    () => new HttpResponse(null, { status: 202 }),
  ),
  http.post(
    api("/api/v1/auth/password-reset/confirm"),
    () => new HttpResponse(null, { status: 204 }),
  ),
  http.post(
    api("/api/v1/auth/logout"),
    () => new HttpResponse(null, { status: 204 }),
  ),
  http.get(api("/api/v1/admin/users"), () => HttpResponse.json(adminUsers)),
  // Anonymous by default; tests with a live session override this with demoUser.
  http.get(api("/api/v1/auth/me"), () =>
    HttpResponse.json({ message: "Unauthorized" }, { status: 401 }),
  ),
];
