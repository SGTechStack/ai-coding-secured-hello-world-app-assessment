export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";

const CSRF_COOKIE_NAME = "XSRF-TOKEN";
const CSRF_HEADER_NAME = "X-XSRF-TOKEN";

/**
 * Header carrying a re-entered password for an irreversible action. Kept in one
 * place so both callers spell it identically; a typo here would read as a
 * missing header and be refused, which is at least a safe failure.
 */
export const CONFIRM_PASSWORD_HEADER = "X-Confirm-Password";

function readCookie(name: string): string | undefined {
  const prefix = `${name}=`;
  const cookie = document.cookie.split("; ").find((entry) => entry.startsWith(prefix));
  return cookie ? decodeURIComponent(cookie.slice(prefix.length)) : undefined;
}

/**
 * Ensures the XSRF-TOKEN cookie is present, fetching it from the backend
 * if it isn't yet (e.g. first load of the SPA), then returns the header
 * to attach to a state-changing request.
 *
 * The backend uses Spring Security's cookie-based CSRF protection: the
 * token cookie is readable by JS (not HttpOnly) specifically so the SPA
 * can echo it back as a header, proving the request came from a page
 * that could read the cookie (i.e. same-origin, since the browser's
 * same-origin policy prevents a hostile page from reading it cross-origin).
 */
export async function csrfHeader(): Promise<Record<string, string>> {
  let token = readCookie(CSRF_COOKIE_NAME);

  if (!token) {
    await fetch(`${API_BASE_URL}/api/csrf`, { credentials: "include" });
    token = readCookie(CSRF_COOKIE_NAME);
  }

  return token ? { [CSRF_HEADER_NAME]: token } : {};
}

interface ErrorResponseBody {
  message: string;
  details: string[];
}

/**
 * An error surfaced from the backend's uniform error response shape
 * (`{ message, details }`), as opposed to a network-level failure.
 */
export class ApiError extends Error {
  readonly details: string[];

  constructor(message: string, details: string[] = []) {
    super(message);
    this.name = "ApiError";
    this.details = details;
  }
}

/**
 * True when a rejection came from an {@link AbortController} cancelling the
 * request rather than from a real failure. Callers should stay silent on
 * these: an aborted request is one the caller itself no longer wants, so
 * rendering it as an error would be misleading.
 */
export function isAbortError(error: unknown): boolean {
  return error instanceof DOMException && error.name === "AbortError";
}

export async function parseErrorResponse(response: Response): Promise<never> {
  let body: Partial<ErrorResponseBody> | undefined;
  try {
    body = (await response.json()) as ErrorResponseBody;
  } catch {
    // response had no JSON body; fall through to the generic message
  }

  throw new ApiError(
    body?.message ?? `Request failed with status ${response.status}`,
    body?.details ?? [],
  );
}

export interface HealthResponse {
  status: string;
  timestamp: string;
}

/**
 * Calls the backend's unauthenticated health endpoint across origins.
 * `credentials: "include"` is set now so the pattern is already in place
 * once session cookies exist for authenticated calls.
 */
export async function fetchHealth(): Promise<HealthResponse> {
  const response = await fetch(`${API_BASE_URL}/api/health`, {
    method: "GET",
    credentials: "include",
  });

  if (!response.ok) {
    throw new Error(`Health check failed with status ${response.status}`);
  }

  return response.json() as Promise<HealthResponse>;
}
