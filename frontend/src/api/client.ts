/**
 * The only place the app talks HTTP. Responsibilities:
 *  - always send the session cookie (credentials: 'include');
 *  - attach the CSRF token to every state-changing request, refreshing it once if the
 *    server says it is stale;
 *  - turn RFC 9457 problem responses into a typed ApiError.
 */

export const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080').replace(
  /\/+$/,
  '',
);

export const CSRF_FAILURE_DETAIL = 'CSRF token missing or invalid';

export type HttpMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';

export interface FieldError {
  field: string;
  message: string;
}

export interface ProblemDetail {
  type?: string;
  title?: string;
  status: number;
  detail?: string;
  errors?: FieldError[];
  correlationId?: string;
}

export class ApiError extends Error {
  readonly status: number;
  readonly problem: ProblemDetail;

  constructor(status: number, problem: ProblemDetail) {
    super(problem.detail ?? problem.title ?? `Request failed with status ${status}`);
    this.name = 'ApiError';
    this.status = status;
    this.problem = problem;
  }

  get fieldErrors(): Record<string, string> {
    const out: Record<string, string> = {};
    for (const error of this.problem.errors ?? []) {
      if (!(error.field in out)) {
        out[error.field] = error.message;
      }
    }
    return out;
  }

  isCsrfFailure(): boolean {
    return this.status === 403 && this.problem.detail === CSRF_FAILURE_DETAIL;
  }
}

export interface CsrfToken {
  headerName: string;
  token: string;
}

let csrfTokenPromise: Promise<CsrfToken> | null = null;

async function fetchCsrfToken(): Promise<CsrfToken> {
  const response = await fetch(`${API_BASE_URL}/api/auth/csrf`, {
    credentials: 'include',
    headers: { Accept: 'application/json' },
  });
  if (!response.ok) {
    throw await toApiError(response);
  }
  return (await response.json()) as CsrfToken;
}

/** Cached per page load; invalidated after login/logout because the server rotates it. */
export function getCsrfToken(): Promise<CsrfToken> {
  if (!csrfTokenPromise) {
    csrfTokenPromise = fetchCsrfToken().catch((error: unknown) => {
      csrfTokenPromise = null;
      throw error;
    });
  }
  return csrfTokenPromise;
}

export function resetCsrfToken(): void {
  csrfTokenPromise = null;
}

const MUTATING_METHODS: ReadonlySet<HttpMethod> = new Set(['POST', 'PUT', 'PATCH', 'DELETE']);

export interface RequestOptions {
  method?: HttpMethod;
  body?: unknown;
  signal?: AbortSignal;
}

export async function apiFetch<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const method = options.method ?? 'GET';
  const mutating = MUTATING_METHODS.has(method);

  const send = async (): Promise<Response> => {
    const headers: Record<string, string> = { Accept: 'application/json' };
    if (options.body !== undefined) {
      headers['Content-Type'] = 'application/json';
    }
    if (mutating) {
      const csrf = await getCsrfToken();
      headers[csrf.headerName] = csrf.token;
    }
    return fetch(`${API_BASE_URL}${path}`, {
      method,
      headers,
      credentials: 'include',
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
      signal: options.signal,
    });
  };

  let response = await send();

  if (mutating && response.status === 403) {
    const error = await toApiError(response.clone());
    if (!error.isCsrfFailure()) {
      throw error;
    }
    resetCsrfToken();
    response = await send();
  }

  if (!response.ok) {
    throw await toApiError(response);
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}

async function toApiError(response: Response): Promise<ApiError> {
  let problem: ProblemDetail = { status: response.status };
  try {
    const text = await response.text();
    if (text) {
      problem = { ...problem, ...(JSON.parse(text) as Partial<ProblemDetail>) };
    }
  } catch {
    // Non-JSON error body: keep the bare status.
  }
  return new ApiError(response.status, problem);
}
