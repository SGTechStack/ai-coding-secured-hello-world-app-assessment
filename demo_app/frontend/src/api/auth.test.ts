import { http, HttpResponse } from "msw";
import { describe, expect, it } from "vitest";
import { api, demoUser } from "../test/handlers";
import { server } from "../test/server";
import { ApiError, apiRequest } from "./client";
import { login, logout } from "./auth";

const credentials = { username: "johndoe", password: "Password123!" };

/**
 * A server that issues a new CSRF token on every `/csrf` fetch and answers each login and logout
 * with the next status in `replies` (200/204 once they run out). Records the token each
 * state-changing request carried.
 */
function fakeServer(replies: number[] = []) {
  const counts = { primes: 0, attempts: 0 };
  const tokensSent: (string | null)[] = [];
  const reply = (request: Request, ok: () => Response) => {
    tokensSent.push(request.headers.get("X-XSRF-TOKEN"));
    const status = replies[counts.attempts] ?? 200;
    counts.attempts += 1;
    return status === 200
      ? ok()
      : HttpResponse.json({ message: "Nope" }, { status });
  };
  server.use(
    http.get(api("/api/v1/auth/csrf"), () => {
      counts.primes += 1;
      return HttpResponse.json({ token: `token-${counts.primes}` });
    }),
    http.post(api("/api/v1/auth/login"), ({ request }) =>
      reply(request, () => HttpResponse.json(demoUser)),
    ),
    http.post(api("/api/v1/auth/logout"), ({ request }) =>
      reply(request, () => new HttpResponse(null, { status: 204 })),
    ),
    http.post(api("/api/v1/thing"), ({ request }) =>
      reply(request, () => HttpResponse.json({})),
    ),
  );
  return { counts, tokensSent };
}

async function rejection(promise: Promise<unknown>): Promise<ApiError> {
  const error = await promise.then(
    () => {
      throw new Error("expected the request to fail");
    },
    (e: unknown) => e,
  );
  expect(error).toBeInstanceOf(ApiError);
  return error as ApiError;
}

describe("CSRF token lifecycle", () => {
  it("drops the token after a successful login, so the next request fetches a fresh one", async () => {
    const { counts, tokensSent } = fakeServer();

    await expect(login(credentials)).resolves.toEqual(demoUser);
    await apiRequest("/api/v1/thing", { method: "POST" });

    expect(counts.primes).toBe(2);
    expect(tokensSent).toEqual(["token-1", "token-2"]);
  });

  it("drops the token after logout", async () => {
    const { counts, tokensSent } = fakeServer();

    await logout();
    await apiRequest("/api/v1/thing", { method: "POST" });

    expect(counts.primes).toBe(2);
    expect(tokensSent).toEqual(["token-1", "token-2"]);
  });

  it("keeps the token when login fails", async () => {
    const { counts } = fakeServer([401]);

    await rejection(login(credentials));
    await apiRequest("/api/v1/thing", { method: "POST" });

    expect(counts.primes).toBe(1);
  });
});

describe("CSRF retry", () => {
  it("retries a 403 once with a freshly fetched token", async () => {
    const { counts, tokensSent } = fakeServer([403]);

    await expect(login(credentials)).resolves.toEqual(demoUser);

    expect(counts).toEqual({ primes: 2, attempts: 2 });
    expect(tokensSent).toEqual(["token-1", "token-2"]);
  });

  it("gives up after a second 403", async () => {
    const { counts } = fakeServer([403, 403]);

    const error = await rejection(logout());

    expect(error.status).toBe(403);
    expect(counts).toEqual({ primes: 2, attempts: 2 });
  });

  it.each([400, 409, 429])("does not retry a %i", async (status) => {
    const { counts } = fakeServer([status]);

    const error = await rejection(login(credentials));

    expect(error.status).toBe(status);
    expect(counts).toEqual({ primes: 1, attempts: 1 });
  });
});
