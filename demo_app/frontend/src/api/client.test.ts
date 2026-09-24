import { http, HttpResponse } from "msw";
import { describe, expect, it } from "vitest";
import { api, csrfResponse, csrfToken } from "../test/handlers";
import { server } from "../test/server";
import { API_BASE_URL, ApiError, apiRequest, dropCsrfToken } from "./client";

type Method = "get" | "post" | "patch" | "delete";

function captureRequests(
  method: Method,
  path: string,
  respond: () => Response | Promise<Response>,
) {
  const requests: Request[] = [];
  server.use(
    http[method](api(path), ({ request }) => {
      requests.push(request.clone());
      return respond();
    }),
  );
  return requests;
}

/** Counts `/csrf` fetches, answering each with `token` (the shared test token by default). */
function captureCsrfFetches(respond: () => Response = csrfResponse) {
  return captureRequests("get", "/api/v1/auth/csrf", respond);
}

async function failure(promise: Promise<unknown>): Promise<ApiError> {
  const error = await promise.then(
    () => {
      throw new Error("expected the request to fail");
    },
    (e: unknown) => e,
  );
  expect(error).toBeInstanceOf(ApiError);
  return error as ApiError;
}

describe("apiRequest", () => {
  it("calls the API origin, not the SPA's, and returns the parsed JSON body", async () => {
    const gets = captureRequests("get", "/api/v1/thing", () =>
      HttpResponse.json({ ok: true }),
    );

    await expect(apiRequest("/api/v1/thing")).resolves.toEqual({ ok: true });

    expect(API_BASE_URL).toBe("http://localhost:8080");
    expect(gets[0]?.url).toBe("http://localhost:8080/api/v1/thing");
  });

  it("sends credentials cross-origin on every request, including the CSRF fetch", async () => {
    const primes = captureCsrfFetches();
    const gets = captureRequests("get", "/api/v1/thing", () =>
      HttpResponse.json({}),
    );
    const posts = captureRequests("post", "/api/v1/thing", () =>
      HttpResponse.json({}),
    );

    await apiRequest("/api/v1/thing");
    await apiRequest("/api/v1/thing", { method: "POST", body: {} });

    expect([...primes, ...gets, ...posts].map((r) => r.credentials)).toEqual([
      "include",
      "include",
      "include",
    ]);
  });

  it("fetches the CSRF token once from the /csrf body and sends it as X-XSRF-TOKEN", async () => {
    const primes = captureCsrfFetches();
    const posts = captureRequests("post", "/api/v1/thing", () =>
      HttpResponse.json({}),
    );

    await apiRequest("/api/v1/thing", { method: "POST", body: { a: 1 } });
    await apiRequest("/api/v1/thing", { method: "POST", body: { a: 2 } });

    expect(primes).toHaveLength(1);
    expect(posts.map((r) => r.headers.get("X-XSRF-TOKEN"))).toEqual([
      csrfToken,
      csrfToken,
    ]);
    expect(posts[0]?.headers.get("Content-Type")).toBe("application/json");
    expect(await posts[0]?.json()).toEqual({ a: 1 });
  });

  it("uses the body token, never a cookie, and keeps it out of web storage", async () => {
    document.cookie = "XSRF-TOKEN=cookie-token; Path=/";
    const posts = captureRequests("post", "/api/v1/thing", () =>
      HttpResponse.json({}),
    );

    try {
      await apiRequest("/api/v1/thing", { method: "POST", body: {} });
    } finally {
      document.cookie = "XSRF-TOKEN=; Max-Age=0; Path=/";
    }

    expect(posts[0]?.headers.get("X-XSRF-TOKEN")).toBe(csrfToken);
    expect(localStorage.length).toBe(0);
    expect(sessionStorage.length).toBe(0);
  });

  it("is lazy: fetches no CSRF token for GET requests", async () => {
    const primes = captureCsrfFetches();
    const gets = captureRequests("get", "/api/v1/thing", () =>
      HttpResponse.json({}),
    );

    await apiRequest("/api/v1/thing");

    expect(primes).toHaveLength(0);
    expect(gets[0]?.headers.has("X-XSRF-TOKEN")).toBe(false);
  });

  it("shares one CSRF fetch between concurrent state-changing requests", async () => {
    const primes = captureCsrfFetches();
    captureRequests("post", "/api/v1/thing", () => HttpResponse.json({}));

    await Promise.all([
      apiRequest("/api/v1/thing", { method: "POST" }),
      apiRequest("/api/v1/thing", { method: "POST" }),
    ]);

    expect(primes).toHaveLength(1);
  });

  it("fetches a fresh CSRF token after dropCsrfToken()", async () => {
    let issued = 0;
    const primes = captureCsrfFetches(() => {
      issued += 1;
      return HttpResponse.json({ token: `token-${issued}` });
    });
    const posts = captureRequests("post", "/api/v1/thing", () =>
      HttpResponse.json({}),
    );

    await apiRequest("/api/v1/thing", { method: "POST" });
    dropCsrfToken();
    await apiRequest("/api/v1/thing", { method: "POST" });

    expect(primes).toHaveLength(2);
    expect(posts.map((r) => r.headers.get("X-XSRF-TOKEN"))).toEqual([
      "token-1",
      "token-2",
    ]);
  });

  it("does not keep a failed CSRF fetch: the next request tries again", async () => {
    let attempts = 0;
    captureCsrfFetches(() => {
      attempts += 1;
      return attempts === 1 ? HttpResponse.error() : csrfResponse();
    });
    const posts = captureRequests("post", "/api/v1/thing", () =>
      HttpResponse.json({}),
    );

    const error = await failure(
      apiRequest("/api/v1/thing", { method: "POST" }),
    );
    await apiRequest("/api/v1/thing", { method: "POST" });

    expect(error.kind).toBe("unavailable");
    expect(attempts).toBe(2);
    expect(posts).toHaveLength(1);
  });

  it("treats a /csrf reply without a token as unavailable", async () => {
    captureCsrfFetches(() => HttpResponse.json({}));

    const error = await failure(
      apiRequest("/api/v1/thing", { method: "POST" }),
    );

    expect(error.kind).toBe("unavailable");
  });

  it.each(["patch", "delete"] as const)(
    "sends %s with the CSRF token",
    async (method) => {
      const requests = captureRequests(
        method,
        "/api/v1/thing",
        () => new HttpResponse(null, { status: 204 }),
      );

      await apiRequest<void>("/api/v1/thing", {
        method: method.toUpperCase() as "PATCH" | "DELETE",
      });

      expect(requests[0]?.method).toBe(method.toUpperCase());
      expect(requests[0]?.headers.get("X-XSRF-TOKEN")).toBe(csrfToken);
    },
  );

  it("sends a bodyless POST and resolves an empty 204 without parsing JSON", async () => {
    const posts = captureRequests(
      "post",
      "/api/v1/thing",
      () => new HttpResponse(null, { status: 204 }),
    );

    await expect(
      apiRequest<void>("/api/v1/thing", { method: "POST" }),
    ).resolves.toBeUndefined();

    expect(posts).toHaveLength(1);
    expect(posts[0]?.headers.has("Content-Type")).toBe(false);
    expect(posts[0]?.headers.get("X-XSRF-TOKEN")).toBe(csrfToken);
    expect(await posts[0]?.text()).toBe("");
  });

  it("resolves an empty 202 without parsing JSON", async () => {
    captureRequests(
      "post",
      "/api/v1/thing",
      () => new HttpResponse(null, { status: 202 }),
    );

    await expect(
      apiRequest<void>("/api/v1/thing", { method: "POST", body: {} }),
    ).resolves.toBeUndefined();
  });

  it("classifies 401 as unauthorized and keeps the server message and code", async () => {
    captureRequests("post", "/api/v1/auth/login", () =>
      HttpResponse.json(
        {
          status: 401,
          code: "INVALID_CREDENTIALS",
          message: "Invalid username or password",
        },
        { status: 401 },
      ),
    );

    const error = await failure(
      apiRequest("/api/v1/auth/login", { method: "POST", body: {} }),
    );

    expect(error.kind).toBe("unauthorized");
    expect(error.status).toBe(401);
    expect(error.code).toBe("INVALID_CREDENTIALS");
    expect(error.message).toBe("Invalid username or password");
    expect(error.fieldErrors).toEqual([]);
  });

  it("classifies 429 as throttled", async () => {
    captureRequests("post", "/api/v1/thing", () =>
      HttpResponse.json(
        { code: "TOO_MANY_ATTEMPTS", message: "Too many attempts" },
        { status: 429 },
      ),
    );

    const error = await failure(
      apiRequest("/api/v1/thing", { method: "POST", body: {} }),
    );

    expect(error.kind).toBe("throttled");
    expect(error.status).toBe(429);
    expect(error.code).toBe("TOO_MANY_ATTEMPTS");
  });

  it.each([400, 403, 404, 409])(
    "classifies any other 4xx (%i) as rejected",
    async (status) => {
      captureRequests("post", "/api/v1/thing", () =>
        HttpResponse.json({ message: "Nope" }, { status }),
      );

      const error = await failure(
        apiRequest("/api/v1/thing", { method: "POST", body: {} }),
      );

      expect(error.kind).toBe("rejected");
      expect(error.status).toBe(status);
      expect(error.code).toBeUndefined();
    },
  );

  it("parses fieldErrors, skipping malformed entries", async () => {
    captureRequests("post", "/api/v1/thing", () =>
      HttpResponse.json(
        {
          code: "ACCOUNT_CONFLICT",
          message: "Account already exists",
          fieldErrors: [
            { field: "username", message: "Username is taken" },
            { field: "email" },
            "junk",
            { field: "email", message: "Email is taken" },
          ],
        },
        { status: 409 },
      ),
    );

    const error = await failure(
      apiRequest("/api/v1/thing", { method: "POST", body: {} }),
    );

    expect(error.code).toBe("ACCOUNT_CONFLICT");
    expect(error.fieldErrors).toEqual([
      { field: "username", message: "Username is taken" },
      { field: "email", message: "Email is taken" },
    ]);
  });

  it("classifies 5xx as unavailable, even without a JSON body", async () => {
    captureRequests(
      "get",
      "/api/v1/thing",
      () => new HttpResponse("boom", { status: 503 }),
    );

    const error = await failure(apiRequest("/api/v1/thing"));

    expect(error.kind).toBe("unavailable");
    expect(error.status).toBe(503);
    expect(error.message).toBe("Request failed with status 503");
    expect(error.code).toBeUndefined();
    expect(error.fieldErrors).toEqual([]);
  });

  it("classifies a network failure as unavailable", async () => {
    captureRequests("get", "/api/v1/thing", () => HttpResponse.error());

    const error = await failure(apiRequest("/api/v1/thing"));

    expect(error.kind).toBe("unavailable");
    expect(error.status).toBeUndefined();
  });
});
