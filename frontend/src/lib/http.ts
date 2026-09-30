import { RETURN_TO_KEY } from '@lib/auth';

export interface ProblemDetails {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  fieldErrors?: Record<string, { code: string; message: string }>;
}

export class HttpError extends Error {
  status: number;
  body: ProblemDetails | undefined;
  constructor(status: number, body?: ProblemDetails) {
    super(`HTTP ${status}`);
    this.status = status;
    this.body = body;
  }
}

export interface ApiError extends Error {
  fieldErrors?: Record<string, { code: string; message: string }>;
  status?: number;
}

export function toApiError(err: unknown): never {
  if (err instanceof HttpError) {
    const message = err.body?.detail ?? err.body?.title ?? `HTTP ${err.status}`;
    const apiError = new Error(message) as ApiError;
    if (err.body?.fieldErrors) apiError.fieldErrors = err.body.fieldErrors;
    apiError.status = err.status;
    throw apiError;
  }
  throw err;
}

const CSRF_MUTATING_METHODS = new Set(['POST', 'PUT', 'PATCH', 'DELETE']);

// Guards against multiple concurrent 401s each firing their own redirect.
let redirecting = false;

// Router basepath (e.g. "/app"), stripped of any trailing slash — see main.tsx.
const BASE_PATH = (import.meta.env.VITE_BASE_URL ?? '/').replace(/\/$/, '');

/** Redirect an unauthenticated user to the login page, preserving where to return. */
function redirectToLogin(): void {
  if (redirecting) return;
  const loginUrl = import.meta.env.VITE_LOGIN_URL ?? '/login';
  const { pathname, search, hash } = window.location;
  // Don't clobber a saved returnTo or loop when the 401 came from the login page itself.
  if (pathname !== loginUrl) {
    redirecting = true;
    // Strip the basepath so returnTo is router-relative; router.navigate re-adds it.
    // Otherwise "/app/foo" would round-trip to "/app/app/foo" after re-auth.
    const relativePath = BASE_PATH && pathname.startsWith(BASE_PATH) ? pathname.slice(BASE_PATH.length) : pathname;
    sessionStorage.setItem(RETURN_TO_KEY, (relativePath || '/') + search + hash);
    window.location.href = loginUrl;
  }
}

function getCsrfToken(): string | undefined {
  return document.cookie
    .split('; ')
    .find((row) => row.startsWith('XSRF-TOKEN='))
    ?.split('=')[1];
}

export async function http(input: RequestInfo | URL, init: RequestInit = {}): Promise<Response> {
  const method = (init.method ?? 'GET').toUpperCase();

  if (CSRF_MUTATING_METHODS.has(method)) {
    const token = getCsrfToken();
    if (token) {
      init = {
        ...init,
        headers: {
          'X-XSRF-TOKEN': token,
          ...init.headers,
        },
      };
    }
  }

  const response = await fetch(input, init);

  if (response.status === 401) {
    redirectToLogin();
  }

  return response;
}
