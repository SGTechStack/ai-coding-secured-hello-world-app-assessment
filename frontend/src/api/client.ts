/**
 * The only module that talks to the backend. The session travels in an HttpOnly cookie
 * (`credentials: 'include'`); the CSRF token is fetched from `/csrf`, held in memory only and
 * attached to every state-changing request. Errors come back as RFC 9457 Problem Details.
 */

export type Role = 'USER' | 'ADMIN';

export interface Me {
  id: string;
  username: string;
  email: string;
  role: Role;
}

export interface Registration {
  username: string;
  email: string;
  password: string;
}

/** An Account as an Admin sees it. */
export interface AdminAccount {
  id: string;
  username: string;
  email: string;
  role: Role;
  enabled: boolean;
  locked: boolean;
  createdAt: string;
}

export interface AccountPage {
  content: AdminAccount[];
  page: { size: number; number: number; totalElements: number; totalPages: number };
}

export interface FieldError {
  field: string;
  message: string;
}

/** The stable `code` values the SPA reacts to. The API may send others. */
export const ErrorCode = {
  validationFailed: 'validation failed',
  userExist: 'user exist',
  invalidCredentials: 'invalid credentials',
  serviceUnavailable: 'service unavailable',
  tooManyRequests: 'too many requests',
  forbidden: 'forbidden',
  passwordResetTokenInvalid: 'password reset token expired or invalid',
  selfActionNotAllowed: 'self action not allowed',
  notFound: 'not found',
} as const;

export interface Problem {
  status: number;
  code: string;
  type?: string;
  title?: string;
  detail?: string;
  errors?: FieldError[];
  /** From `Retry-After` on a Throttled (429) response: how long before trying again. */
  retryAfterSeconds?: number;
}

export class ApiError extends Error {
  readonly problem: Problem;

  constructor(problem: Problem) {
    super(problem.detail ?? problem.title ?? `Request failed with status ${problem.status}`);
    this.name = 'ApiError';
    this.problem = problem;
  }

  get status(): number {
    return this.problem.status;
  }

  get code(): string {
    return this.problem.code;
  }
}

export interface ApiClient {
  /** Fetches the CSRF token for this session; call once when the app loads. */
  start(): Promise<void>;
  register(registration: Registration): Promise<void>;
  /** Logs in, then fetches the new session's CSRF token. Rejects only if the login itself fails. */
  login(username: string, password: string): Promise<void>;
  /**
   * Logs out, then fetches a new CSRF token. Rejects if the server refused, e.g. because the
   * session had already expired; the old token is dropped either way.
   */
  logout(): Promise<void>;
  me(): Promise<Me>;
  hello(): Promise<string>;
  /** Always answered the same way, whether or not the email is registered. */
  requestPasswordReset(email: string): Promise<void>;
  confirmPasswordReset(token: string, newPassword: string): Promise<void>;
  listAccounts(page: { page: number; size: number }): Promise<AccountPage>;
  setAccountEnabled(id: string, enabled: boolean): Promise<AdminAccount>;
  unlockAccount(id: string): Promise<AdminAccount>;
  changeAccountRole(id: string, role: Role): Promise<AdminAccount>;
  deleteAccount(id: string): Promise<void>;
  /**
   * Called whenever a request gets 401 because the session is gone, so the app can clear its
   * state and return to login. A failed login attempt doesn't count.
   */
  setUnauthenticatedHandler(handler: (() => void) | undefined): void;
}

interface CsrfResponse {
  token: string;
  headerName: string;
}

export function createApiClient(options: { baseUrl: string; fetch?: typeof fetch }): ApiClient {
  const { baseUrl } = options;
  const doFetch = options.fetch ?? ((input, init) => fetch(input, init));
  let csrf: CsrfResponse | undefined;
  let onUnauthenticated: (() => void) | undefined;

  /** `loginAttempt`: a 401 answers the attempt; to any other request it means the session is gone. */
  async function request(path: string, init: RequestInit = {}, { loginAttempt = false } = {}): Promise<Response> {
    const response = await doFetch(`${baseUrl}${path}`, { ...init, credentials: 'include' });
    if (!response.ok) {
      const problem = await readProblem(response);
      if (response.status === 401 && !loginAttempt) {
        csrf = undefined;
        onUnauthenticated?.();
      }
      throw new ApiError(problem);
    }
    return response;
  }

  async function refreshCsrf(): Promise<void> {
    const response = await request('/csrf');
    const body = (await response.json()) as CsrfResponse;
    csrf = { token: body.token, headerName: body.headerName };
  }

  /**
   * The session and its CSRF token change at login and logout, so the old token is useless.
   * A failed refresh is ignored: send() fetches a token before the next change.
   */
  async function renewCsrf(): Promise<void> {
    csrf = undefined;
    await refreshCsrf().catch(() => undefined);
  }

  async function send(
    path: string,
    {
      method = 'POST',
      body,
      contentType,
      loginAttempt,
    }: { method?: string; body?: string; contentType?: string; loginAttempt?: boolean } = {},
  ): Promise<Response> {
    if (!csrf) {
      await refreshCsrf();
    }
    const { headerName, token } = csrf!;
    const init = {
      method,
      headers: { ...(contentType && { 'Content-Type': contentType }), [headerName]: token },
      body,
    };
    return request(path, init, { loginAttempt });
  }

  function sendJson(path: string, value: unknown, method = 'POST'): Promise<Response> {
    return send(path, { method, body: JSON.stringify(value), contentType: 'application/json' });
  }

  const accountPath = (id: string) => `/admin/users/${encodeURIComponent(id)}`;

  return {
    start: refreshCsrf,

    async register(registration) {
      await sendJson('/register', registration);
    },

    async login(username, password) {
      await send('/login', {
        body: new URLSearchParams({ username, password }).toString(),
        contentType: 'application/x-www-form-urlencoded',
        loginAttempt: true,
      });
      await renewCsrf();
    },

    async logout() {
      try {
        await send('/logout');
      } finally {
        await renewCsrf();
      }
    },

    setUnauthenticatedHandler(handler) {
      onUnauthenticated = handler;
    },

    async me() {
      return (await request('/me')).json() as Promise<Me>;
    },

    async hello() {
      return (await request('/hello')).text();
    },

    async requestPasswordReset(email) {
      await sendJson('/password-reset/request', { email });
    },

    async confirmPasswordReset(token, newPassword) {
      await sendJson('/password-reset/confirm', { token, newPassword });
    },

    async listAccounts({ page, size }) {
      const query = new URLSearchParams({ page: String(page), size: String(size) });
      return (await request(`/admin/users?${query}`)).json() as Promise<AccountPage>;
    },

    async setAccountEnabled(id, enabled) {
      return (await sendJson(`${accountPath(id)}/status`, { enabled }, 'PATCH')).json() as Promise<AdminAccount>;
    },

    async unlockAccount(id) {
      return (await send(`${accountPath(id)}/unlock`)).json() as Promise<AdminAccount>;
    },

    async changeAccountRole(id, role) {
      return (await sendJson(`${accountPath(id)}/role`, { role }, 'PATCH')).json() as Promise<AdminAccount>;
    },

    async deleteAccount(id) {
      await send(accountPath(id), { method: 'DELETE' });
    },
  };
}

async function readProblem(response: Response): Promise<Problem> {
  const problem = await readProblemBody(response);
  const retryAfterSeconds = readRetryAfter(response);
  return retryAfterSeconds === undefined ? problem : { ...problem, retryAfterSeconds };
}

async function readProblemBody(response: Response): Promise<Problem> {
  const contentType = response.headers.get('Content-Type') ?? '';
  if (contentType.includes('json')) {
    try {
      const body = (await response.json()) as Partial<Problem>;
      if (typeof body.code === 'string') {
        return { ...body, status: body.status ?? response.status, code: body.code };
      }
    } catch {
      // Not a Problem Details body; fall through.
    }
  }
  return { status: response.status, code: 'error' };
}

/** The API sends `Retry-After` as whole seconds; any other form is treated as unknown. */
function readRetryAfter(response: Response): number | undefined {
  const value = response.headers.get('Retry-After') ?? '';
  return /^\d+$/.test(value) && Number(value) > 0 ? Number(value) : undefined;
}

export const api = createApiClient({ baseUrl: import.meta.env.VITE_API_BASE_URL ?? '/api' });
