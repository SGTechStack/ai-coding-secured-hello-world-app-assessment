/**
 * The single fetch wrapper for the backend API. It
 * - sends the session cookie (same-origin; the Vite dev server proxies /api),
 * - attaches the CSRF token (XSRF-TOKEN cookie -> X-XSRF-TOKEN header) to state-changing
 *   requests, priming the cookie first if the server has not issued one yet,
 * - sends a JSON body only when given one, and resolves an empty `204` to `undefined`,
 * - turns every failure into an ApiError with a `kind` the UI can switch on.
 */

/**
 * - `unauthorized`: 401, the request lacked valid credentials or a session.
 * - `rejected`: any other 4xx, e.g. a 403 for a missing or stale CSRF token.
 * - `unavailable`: a 5xx, or the server could not be reached at all.
 */
export type ApiErrorKind = "unauthorized" | "rejected" | "unavailable";

export class ApiError extends Error {
  readonly kind: ApiErrorKind;
  readonly status: number | undefined;

  constructor(kind: ApiErrorKind, message: string, status?: number) {
    super(message);
    this.name = "ApiError";
    this.kind = kind;
    this.status = status;
  }
}

type RequestOptions = {
  method?: "GET" | "POST";
  body?: unknown;
};

const CSRF_COOKIE = "XSRF-TOKEN";
const CSRF_HEADER = "X-XSRF-TOKEN";
const CSRF_PRIME_PATH = "/api/v1/auth/csrf";

export async function apiRequest<T>(
  path: string,
  { method = "GET", body }: RequestOptions = {},
): Promise<T> {
  const headers = new Headers({ Accept: "application/json" });
  if (body !== undefined) headers.set("Content-Type", "application/json");
  if (method !== "GET") {
    const token = await csrfToken();
    if (token) headers.set(CSRF_HEADER, token);
  }

  const response = await send(path, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  // 204 No Content has no body to parse (e.g. logout); callers type that as `void`.
  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}

async function csrfToken(): Promise<string | undefined> {
  const existing = readCookie(CSRF_COOKIE);
  if (existing) return existing;
  // Any GET under /api/v1/auth makes the server set the cookie.
  await send(CSRF_PRIME_PATH, { method: "GET" });
  return readCookie(CSRF_COOKIE);
}

async function send(path: string, init: RequestInit): Promise<Response> {
  let response: Response;
  try {
    response = await fetch(path, { ...init, credentials: "same-origin" });
  } catch {
    throw new ApiError("unavailable", "Network request failed");
  }
  if (response.ok) return response;

  throw new ApiError(
    errorKind(response.status),
    await errorMessage(response),
    response.status,
  );
}

function errorKind(status: number): ApiErrorKind {
  if (status === 401) return "unauthorized";
  if (status >= 400 && status < 500) return "rejected";
  return "unavailable";
}

/** Drops the current CSRF token and has the server issue a fresh one. */
export async function refreshCsrfToken(): Promise<void> {
  document.cookie = `${CSRF_COOKIE}=; Max-Age=0; Path=/`;
  await send(CSRF_PRIME_PATH, { method: "GET" });
}

async function errorMessage(response: Response): Promise<string> {
  try {
    const body: unknown = await response.json();
    if (
      body &&
      typeof body === "object" &&
      "message" in body &&
      typeof body.message === "string"
    ) {
      return body.message;
    }
  } catch {
    // Not JSON (e.g. a proxy error page); fall through to the generic message.
  }
  return `Request failed with status ${response.status}`;
}

function readCookie(name: string): string | undefined {
  const prefix = `${name}=`;
  const cookie = document.cookie.split("; ").find((c) => c.startsWith(prefix));
  return cookie === undefined
    ? undefined
    : decodeURIComponent(cookie.slice(prefix.length));
}
