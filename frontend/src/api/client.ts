const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";

export interface HealthResponse {
  status: string;
  timestamp: string;
}

export interface RegistrationRequest {
  username: string;
  email: string;
  password: string;
}

export interface RegistrationResponse {
  id: string;
  username: string;
  email: string;
  role: "USER" | "ADMIN";
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
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(request),
  });

  if (!response.ok) {
    return parseErrorResponse(response);
  }

  return response.json() as Promise<RegistrationResponse>;
}
