import type { ApiErrorBody } from "./types";

/**
 * The backend's origin. Different from this app's origin by design, so every request below is a
 * cross-origin credentialed request — which is exactly the case the PRD wants exercised rather than
 * hidden behind a dev proxy.
 */
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";

/** A failed response, carrying the parts a UI actually needs to react to. */
export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly fieldErrors: Record<string, string>;

  constructor(status: number, body: ApiErrorBody | null, fallback: string) {
    super(body?.message ?? fallback);
    this.name = "ApiError";
    this.status = status;
    this.code = body?.code ?? "unknown_error";
    this.fieldErrors = body?.fieldErrors ?? {};
  }
}

/**
 * The CSRF token, cached for the lifetime of the page.
 *
 * It arrives from a bootstrap endpoint rather than from a cookie, because the frontend and backend
 * are separate origins and `document.cookie` here cannot see a cookie set there. The cookie still
 * travels with each request for the server to compare against; this is only about how the value
 * reaches JavaScript.
 */
let csrfToken: string | null = null;
let csrfHeaderName = "X-XSRF-TOKEN";
let csrfRequest: Promise<string> | null = null;

async function fetchCsrfToken(): Promise<string> {
  const response = await fetch(`${API_BASE_URL}/api/auth/csrf`, {
    credentials: "include",
  });
  if (!response.ok) {
    throw new ApiError(response.status, null, "Could not reach the server.");
  }
  const body = (await response.json()) as { headerName: string; token: string };
  csrfHeaderName = body.headerName;
  csrfToken = body.token;
  return body.token;
}

/**
 * Returns the cached token, fetching it if needed.
 *
 * The in-flight promise is shared so that several components mounting at once produce one request
 * instead of a small stampede, each overwriting the last.
 */
async function ensureCsrfToken(): Promise<string> {
  if (csrfToken) return csrfToken;
  csrfRequest ??= fetchCsrfToken().finally(() => {
    csrfRequest = null;
  });
  return csrfRequest;
}

type Method = "GET" | "POST" | "PATCH" | "DELETE";

interface RequestOptions {
  method?: Method;
  body?: unknown;
  /** Set for endpoints that answer in plain text, like the greeting. */
  expect?: "json" | "text" | "none";
}

async function send(path: string, options: RequestOptions, retryOnCsrfFailure: boolean) {
  const method = options.method ?? "GET";
  const headers: Record<string, string> = { Accept: "application/json, text/plain" };

  if (options.body !== undefined) {
    headers["Content-Type"] = "application/json";
  }
  // Reads do not need a token, and asking for one would make the very first page load wait on a
  // request it does not need.
  if (method !== "GET") {
    headers[csrfHeaderName] = await ensureCsrfToken();
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    method,
    headers,
    // Without this the session cookie is neither sent nor stored, and every authenticated request
    // silently looks like a logged-out one.
    credentials: "include",
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
  });

  if (!response.ok) {
    const body = await readErrorBody(response);
    // A token can go stale — after a server restart, or once the cookie expires. Refetching once and
    // retrying turns a dead-end 403 into a request that works, instead of asking the user to reload.
    if (response.status === 403 && body?.code === "invalid_csrf_token" && retryOnCsrfFailure) {
      csrfToken = null;
      return send(path, options, false);
    }
    throw new ApiError(response.status, body, "Something went wrong.");
  }

  return response;
}

async function readErrorBody(response: Response): Promise<ApiErrorBody | null> {
  try {
    return (await response.json()) as ApiErrorBody;
  } catch {
    // An error without a JSON body — a proxy or a crash rather than the application.
    return null;
  }
}

export async function apiJson<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const response = await send(path, options, true);
  return (await response.json()) as T;
}

export async function apiText(path: string, options: RequestOptions = {}): Promise<string> {
  const response = await send(path, options, true);
  return response.text();
}

export async function apiVoid(path: string, options: RequestOptions = {}): Promise<void> {
  await send(path, options, true);
}

/** Dropped on logout so the next login does not reuse a token tied to the old session. */
export function forgetCsrfToken(): void {
  csrfToken = null;
}
