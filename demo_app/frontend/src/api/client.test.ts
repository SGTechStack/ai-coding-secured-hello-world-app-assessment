import { http, HttpResponse } from "msw";
import { describe, expect, it } from "vitest";
import { csrfResponse, csrfToken } from "../test/handlers";
import { server } from "../test/server";
import { ApiError, apiRequest, refreshCsrfToken } from "./client";

function captureRequests(
  method: "get" | "post",
  path: string,
  respond: () => Response,
) {
  const requests: Request[] = [];
  server.use(
    http[method](path, ({ request }) => {
      requests.push(request.clone());
      return respond();
    }),
  );
  return requests;
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
  it("returns the parsed JSON body on success", async () => {
    server.use(
      http.get("/api/v1/thing", () => HttpResponse.json({ ok: true })),
    );

    await expect(apiRequest("/api/v1/thing")).resolves.toEqual({ ok: true });
  });

  it("primes the CSRF cookie, then sends it as X-XSRF-TOKEN on state-changing requests", async () => {
    const primes = captureRequests("get", "/api/v1/auth/csrf", () => {
      return csrfResponse();
    });
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

  it("does not attach a CSRF header to GET requests", async () => {
    const gets = captureRequests("get", "/api/v1/thing", () =>
      HttpResponse.json({}),
    );

    await apiRequest("/api/v1/thing");

    expect(gets[0]?.headers.has("X-XSRF-TOKEN")).toBe(false);
  });

  it("classifies 401 as unauthorized and keeps the server message", async () => {
    server.use(
      http.post("/api/v1/auth/login", () =>
        HttpResponse.json(
          { message: "Invalid username or password" },
          { status: 401 },
        ),
      ),
    );

    const error = await failure(
      apiRequest("/api/v1/auth/login", { method: "POST", body: {} }),
    );

    expect(error.kind).toBe("unauthorized");
    expect(error.status).toBe(401);
    expect(error.message).toBe("Invalid username or password");
  });

  it.each([400, 403, 404])(
    "classifies any other 4xx (%i) as rejected",
    async (status) => {
      server.use(
        http.post("/api/v1/thing", () =>
          HttpResponse.json({ message: "Forbidden" }, { status }),
        ),
      );

      const error = await failure(
        apiRequest("/api/v1/thing", { method: "POST", body: {} }),
      );

      expect(error.kind).toBe("rejected");
      expect(error.status).toBe(status);
    },
  );

  it("classifies 5xx as unavailable, even without a JSON body", async () => {
    server.use(
      http.get(
        "/api/v1/thing",
        () => new HttpResponse("boom", { status: 503 }),
      ),
    );

    const error = await failure(apiRequest("/api/v1/thing"));

    expect(error.kind).toBe("unavailable");
    expect(error.status).toBe(503);
  });

  it("classifies a network failure as unavailable", async () => {
    server.use(http.get("/api/v1/thing", () => HttpResponse.error()));

    const error = await failure(apiRequest("/api/v1/thing"));

    expect(error.kind).toBe("unavailable");
    expect(error.status).toBeUndefined();
  });
});

describe("refreshCsrfToken", () => {
  it("drops the stale token and asks the server for a fresh one", async () => {
    document.cookie = "XSRF-TOKEN=stale-token; Path=/";
    const primes = captureRequests(
      "get",
      "/api/v1/auth/csrf",
      () => new HttpResponse(null, { status: 204 }),
    );

    await refreshCsrfToken();

    expect(primes).toHaveLength(1);
    expect(document.cookie).not.toContain("stale-token");
  });
});
