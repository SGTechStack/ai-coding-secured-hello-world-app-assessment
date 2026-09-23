const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";
const CSRF_COOKIE_NAME = "XSRF-TOKEN";
const CSRF_HEADER_NAME = "X-XSRF-TOKEN";

export interface HealthResponse {
  status: string;
  timestamp: string;
}

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
async function csrfHeader(): Promise<Record<string, string>> {
  let token = readCookie(CSRF_COOKIE_NAME);

  if (!token) {
    await fetch(`${API_BASE_URL}/api/csrf`, { credentials: "include" });
    token = readCookie(CSRF_COOKIE_NAME);
  }

  return token ? { [CSRF_HEADER_NAME]: token } : {};
}

export type UserRole = "USER" | "ADMIN";

export interface RegistrationRequest {
  username: string;
  email: string;
  password: string;
}

export interface RegistrationResponse {
  id: string;
  username: string;
  email: string;
  role: UserRole;
  enabled: boolean;
  createdAt: string;
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

async function parseErrorResponse(response: Response): Promise<never> {
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

/**
 * Registers a new account. Throws {@link ApiError} on validation failures
 * (400) or username/email conflicts (409), with the backend's message and
 * field-level details attached.
 */
export async function register(
  request: RegistrationRequest,
): Promise<RegistrationResponse> {
  const response = await fetch(`${API_BASE_URL}/api/auth/register`, {
    method: "POST",
    credentials: "include",
    headers: { "Content-Type": "application/json", ...(await csrfHeader()) },
    body: JSON.stringify(request),
  });

  if (!response.ok) {
    return parseErrorResponse(response);
  }

  return response.json() as Promise<RegistrationResponse>;
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface LoginResponse {
  username: string;
  role: UserRole;
}

/**
 * Logs in with username and password. On success, the backend sets a
 * secure session cookie. On failure (wrong password or unknown username)
 * throws {@link ApiError} with the same generic message either way, so the
 * UI cannot be used to probe for valid usernames.
 */
export async function login(request: LoginRequest): Promise<LoginResponse> {
  const body = new URLSearchParams({
    username: request.username,
    password: request.password,
  });

  const response = await fetch(`${API_BASE_URL}/api/auth/login`, {
    method: "POST",
    credentials: "include",
    headers: {
      "Content-Type": "application/x-www-form-urlencoded",
      ...(await csrfHeader()),
    },
    body: body.toString(),
  });

  if (!response.ok) {
    return parseErrorResponse(response);
  }

  return response.json() as Promise<LoginResponse>;
}

/**
 * Logs out, invalidating the server-side session and clearing the cookie.
 */
export async function logout(): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/api/auth/logout`, {
    method: "POST",
    credentials: "include",
    headers: await csrfHeader(),
  });

  if (!response.ok) {
    return parseErrorResponse(response);
  }
}

/**
 * Requests a password reset link for the given email. Always resolves
 * with the same generic message, whether or not the email is registered
 * — that's the backend's enumeration-resistance guarantee, not something
 * the client can or should try to distinguish.
 */
export async function requestPasswordReset(email: string): Promise<string> {
  const response = await fetch(`${API_BASE_URL}/api/auth/password-reset/request`, {
    method: "POST",
    credentials: "include",
    headers: { "Content-Type": "application/json", ...(await csrfHeader()) },
    body: JSON.stringify({ email }),
  });

  if (!response.ok) {
    return parseErrorResponse(response);
  }

  const body = (await response.json()) as { message: string };
  return body.message;
}

/**
 * Confirms a password reset with the token from the reset link. Throws
 * {@link ApiError} for an invalid/expired/already-used token (400) or a
 * password failing the strength policy (400).
 */
export async function confirmPasswordReset(token: string, newPassword: string): Promise<string> {
  const response = await fetch(`${API_BASE_URL}/api/auth/password-reset/confirm`, {
    method: "POST",
    credentials: "include",
    headers: { "Content-Type": "application/json", ...(await csrfHeader()) },
    body: JSON.stringify({ token, newPassword }),
  });

  if (!response.ok) {
    return parseErrorResponse(response);
  }

  const body = (await response.json()) as { message: string };
  return body.message;
}

export interface AdminUserSummary {
  id: string;
  username: string;
  email: string;
  role: UserRole;
  enabled: boolean;
  createdAt: string;
}

/**
 * Lists all registered users. Admin-only: throws {@link ApiError} with a
 * 403-flavoured message for a non-admin session, 401 for no session at
 * all — the role check happens entirely server-side, this call just
 * surfaces whatever the backend decides.
 */
export async function fetchAdminUsers(signal?: AbortSignal): Promise<AdminUserSummary[]> {
  const response = await fetch(`${API_BASE_URL}/api/admin/users`, {
    method: "GET",
    credentials: "include",
    signal,
  });

  if (!response.ok) {
    return parseErrorResponse(response);
  }

  return response.json() as Promise<AdminUserSummary[]>;
}

/**
 * Enables or disables another user's account. Throws {@link ApiError}
 * (400) if the target is the caller's own account — the backend rejects
 * self-targeting outright, this call just surfaces that.
 */
export async function setUserEnabled(userId: string, enabled: boolean): Promise<AdminUserSummary> {
  const response = await fetch(`${API_BASE_URL}/api/admin/users/${userId}/enabled`, {
    method: "PATCH",
    credentials: "include",
    headers: { "Content-Type": "application/json", ...(await csrfHeader()) },
    body: JSON.stringify({ enabled }),
  });

  if (!response.ok) {
    return parseErrorResponse(response);
  }

  return response.json() as Promise<AdminUserSummary>;
}

/**
 * Changes another user's role. Throws {@link ApiError} (400) if the
 * target is the caller's own account.
 */
export async function setUserRole(userId: string, role: UserRole): Promise<AdminUserSummary> {
  const response = await fetch(`${API_BASE_URL}/api/admin/users/${userId}/role`, {
    method: "PATCH",
    credentials: "include",
    headers: { "Content-Type": "application/json", ...(await csrfHeader()) },
    body: JSON.stringify({ role }),
  });

  if (!response.ok) {
    return parseErrorResponse(response);
  }

  return response.json() as Promise<AdminUserSummary>;
}

/**
 * Deletes another user's account. Throws {@link ApiError} (400) if the
 * target is the caller's own account.
 */
export async function deleteUser(userId: string): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/api/admin/users/${userId}`, {
    method: "DELETE",
    credentials: "include",
    headers: await csrfHeader(),
  });

  if (!response.ok) {
    return parseErrorResponse(response);
  }
}

/**
 * Calls the protected greeting endpoint. Throws {@link ApiError} with a
 * 401-flavoured message if there is no valid session.
 */
export async function fetchGreeting(signal?: AbortSignal): Promise<string> {
  const response = await fetch(`${API_BASE_URL}/api/hello`, {
    method: "GET",
    credentials: "include",
    signal,
  });

  if (!response.ok) {
    return parseErrorResponse(response);
  }

  return response.text();
}
