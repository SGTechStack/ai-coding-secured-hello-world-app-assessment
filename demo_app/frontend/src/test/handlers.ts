import { http, HttpResponse } from "msw";
import { API_BASE_URL } from "../api/client";

export const demoUser = { username: "johndoe", firstName: "John" };
export const csrfToken = "test-xsrf-token";

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
  http.post(
    api("/api/v1/auth/logout"),
    () => new HttpResponse(null, { status: 204 }),
  ),
  // Anonymous by default; tests with a live session override this with demoUser.
  http.get(api("/api/v1/auth/me"), () =>
    HttpResponse.json({ message: "Unauthorized" }, { status: 401 }),
  ),
];
