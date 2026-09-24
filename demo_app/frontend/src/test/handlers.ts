import { http, HttpResponse } from "msw";

export const demoUser = { username: "johndoe", firstName: "John" };
export const csrfToken = "test-xsrf-token";

/**
 * Happy-path API. Tests override per case with `server.use(...)`.
 * The CSRF endpoint sets the cookie the way Spring's CookieCsrfTokenRepository does.
 */
export const handlers = [
  http.get("/api/v1/auth/csrf", () => {
    return new HttpResponse(null, {
      status: 204,
      headers: { "Set-Cookie": `XSRF-TOKEN=${csrfToken}; Path=/` },
    });
  }),
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
