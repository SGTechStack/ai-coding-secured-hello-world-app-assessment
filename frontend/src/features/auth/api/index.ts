import { API_BASE_URL, csrfHeader, parseErrorResponse } from "@/common/api/http";

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

/**
 * Registers a new account. Throws {@link ApiError} on validation failures
 * (400) or username/email conflicts (409), with the backend's message and
 * field-level details attached.
 */
export async function register(request: RegistrationRequest): Promise<RegistrationResponse> {
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
