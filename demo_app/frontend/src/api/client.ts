/**
 * The single fetch wrapper for the backend API, which runs on its own origin. It
 * - prefixes every path with the API base URL (`VITE_API_BASE_URL`, set at build time),
 * - sends the session cookie cross-origin (`credentials: "include"`; the API's CORS allow-list
 *   must name this app's origin),
 * - attaches the CSRF token as the X-XSRF-TOKEN header on state-changing requests. The token is
 *   taken from the `/csrf` response body (the API's cookie is not readable from this origin) and
 *   held in memory only, never in web storage,
 * - sends a JSON body only when given one, and resolves an empty `202`/`204` to `undefined`,
 * - turns every failure into an ApiError with a `kind` the UI can switch on.
 */

import { apiBaseUrl } from "./base-url";

/** The API's origin, without a trailing slash. */
export const API_BASE_URL = apiBaseUrl(import.meta.env.VITE_API_BASE_URL);

/**
 * - `unauthorized`: 401, the request lacked valid credentials or a session.
 * - `throttled`: 429, too many attempts; the user should wait.
 * - `rejected`: any other 4xx, e.g. a 403 for a missing or stale CSRF token, a 400 or a 409.
 * - `unavailable`: a 5xx, or the server could not be reached at all.
 */
export type ApiErrorKind =
  "unauthorized" | "throttled" | "rejected" | "unavailable";

/**
 * The API error codes the UI branches on. Compare through `hasCode`, so a misspelt code is a type
 * error rather than a branch that never runs.
 */
export type ApiErrorCode =
  "INVALID_RESET_TOKEN" | "USER_NOT_FOUND" | "SELF_ACTION_NOT_ALLOWED";

/** Whether `error` is an ApiError carrying `code`. */
export function hasCode(error: unknown, code: ApiErrorCode): error is ApiError {
  return error instanceof ApiError && error.code === code;
}

/** One problem with one request field, as the API reports it. */
export type FieldError = { field: string; message: string };

export class ApiError extends Error {
  readonly kind: ApiErrorKind;
  readonly status: number | undefined;
  /** The API's machine-readable error code (e.g. `INVALID_CREDENTIALS`), when it sent one. */
  readonly code: string | undefined;
  /** Per-field problems from the API; empty when there are none. */
  readonly fieldErrors: readonly FieldError[];

  constructor(
    kind: ApiErrorKind,
    message: string,
    {
      status,
      code,
      fieldErrors = [],
    }: { status?: number; code?: string; fieldErrors?: FieldError[] } = {},
  ) {
    super(message);
    this.name = "ApiError";
    this.kind = kind;
    this.status = status;
    this.code = code;
    this.fieldErrors = fieldErrors;
  }
}

type RequestOptions = {
  method?: "GET" | "POST" | "PATCH" | "DELETE";
  body?: unknown;
};

const CSRF_HEADER = "X-XSRF-TOKEN";
const CSRF_PATH = "/api/v1/auth/csrf";

/**
 * The CSRF token, in memory only. A pending fetch is shared, so concurrent state-changing
 * requests prime it once.
 */
let csrfToken: Promise<string> | undefined;

export async function apiRequest<T>(
  path: string,
  { method = "GET", body }: RequestOptions = {},
): Promise<T> {
  const headers = new Headers({ Accept: "application/json" });
  if (body !== undefined) headers.set("Content-Type", "application/json");
  if (method !== "GET") headers.set(CSRF_HEADER, await currentCsrfToken());

  const response = await send(path, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  // 204 No Content (e.g. logout) and the API's 202 Accepted (reset request) have no body to
  // parse; callers type them as `void`.
  if (response.status === 204 || response.status === 202) return undefined as T;
  return (await response.json()) as T;
}

/**
 * Forgets the CSRF token, so the next state-changing request fetches a fresh one. Call it
 * whenever the server rotates the token: after login, logout, or a password-reset confirm.
 */
export function dropCsrfToken(): void {
  csrfToken = undefined;
}

/**
 * Runs `request`; on a `403` (a missing or stale CSRF token), drops the token and runs it once
 * more, which fetches a fresh one. Any other error, e.g. a `400`, `409` or `429`, is not retried:
 * resending would not change the answer. Every state-changing call goes through this.
 */
export async function withCsrfRetry<T>(request: () => Promise<T>): Promise<T> {
  try {
    return await request();
  } catch (error) {
    if (!(error instanceof ApiError && error.status === 403)) throw error;
    dropCsrfToken();
    return request();
  }
}

function currentCsrfToken(): Promise<string> {
  if (!csrfToken) {
    const pending = fetchCsrfToken();
    csrfToken = pending;
    // A failed fetch must not stick: the next request tries again.
    pending.catch(() => {
      if (csrfToken === pending) csrfToken = undefined;
    });
  }
  return csrfToken;
}

async function fetchCsrfToken(): Promise<string> {
  const response = await send(CSRF_PATH, {
    method: "GET",
    headers: { Accept: "application/json" },
  });
  const body: unknown = await response.json().catch(() => undefined);
  if (isRecord(body) && typeof body.token === "string") return body.token;
  throw new ApiError("unavailable", "The server sent no CSRF token", {
    status: response.status,
  });
}

async function send(path: string, init: RequestInit): Promise<Response> {
  let response: Response;
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      ...init,
      credentials: "include",
    });
  } catch {
    throw new ApiError("unavailable", "Network request failed");
  }
  if (response.ok) return response;
  throw await toApiError(response);
}

function errorKind(status: number): ApiErrorKind {
  if (status === 401) return "unauthorized";
  if (status === 429) return "throttled";
  if (status >= 400 && status < 500) return "rejected";
  return "unavailable";
}

async function toApiError(response: Response): Promise<ApiError> {
  // Not JSON (e.g. a proxy error page) leaves the body undefined and the message generic.
  const body: unknown = await response.json().catch(() => undefined);
  const record = isRecord(body) ? body : {};
  const message =
    typeof record.message === "string"
      ? record.message
      : `Request failed with status ${response.status}`;
  return new ApiError(errorKind(response.status), message, {
    status: response.status,
    code: typeof record.code === "string" ? record.code : undefined,
    fieldErrors: Array.isArray(record.fieldErrors)
      ? record.fieldErrors.filter(isFieldError)
      : [],
  });
}

function isFieldError(value: unknown): value is FieldError {
  return (
    isRecord(value) &&
    typeof value.field === "string" &&
    typeof value.message === "string"
  );
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}
