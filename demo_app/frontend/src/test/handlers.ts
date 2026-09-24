import { http, HttpResponse } from "msw";
import type { UserProfile } from "../api/auth";

export const demoUser: UserProfile = {
  username: "johndoe",
  firstName: "John",
  role: "USER",
};
export const csrfToken = "test-xsrf-token";

/**
 * The API's `GET /api/v1/auth/csrf` reply: the token in the body, and the cookie set the way
 * Spring's CookieCsrfTokenRepository does.
 */
export function csrfResponse() {
  return HttpResponse.json(
    { headerName: "X-XSRF-TOKEN", token: csrfToken },
    { headers: { "Set-Cookie": `XSRF-TOKEN=${csrfToken}; Path=/` } },
  );
}

/** Happy-path API. Tests override per case with `server.use(...)`. */
export const handlers = [
  http.get("/api/v1/auth/csrf", () => csrfResponse()),
  http.post("/api/v1/auth/login", () => HttpResponse.json(demoUser)),
  http.post(
    "/api/v1/auth/logout",
    () => new HttpResponse(null, { status: 204 }),
  ),
  // Anonymous by default; tests with a live session override this with demoUser.
  http.get("/api/v1/auth/me", () =>
    HttpResponse.json({ message: "Unauthorized" }, { status: 401 }),
  ),
];
