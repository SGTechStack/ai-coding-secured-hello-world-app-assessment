import { request, type APIRequestContext, type APIResponse } from '@playwright/test';
import { env } from './env';

export interface ApiResult {
  status: number;
  headers: Record<string, string>;
  /** Every Set-Cookie header on the response (Playwright joins them with '\n'). */
  setCookies: string[];
  text: string;
  json: unknown;
}

export interface SendOptions {
  data?: unknown;
  /** Attach the X-XSRF-TOKEN header (default true for mutating methods). */
  csrf?: boolean;
  headers?: Record<string, string>;
}

/**
 * A backend API client with its own isolated cookie jar — i.e. one
 * "device". Mirrors frontend/src/api.ts: primes the XSRF-TOKEN cookie via
 * GET /api/csrf and echoes it in X-XSRF-TOKEN on mutating requests.
 */
export class ApiClient {
  private constructor(readonly ctx: APIRequestContext) {}

  static async create(storageState?: string, extraHTTPHeaders?: Record<string, string>): Promise<ApiClient> {
    const ctx = await request.newContext({ baseURL: env.backendUrl, storageState, extraHTTPHeaders });
    return new ApiClient(ctx);
  }

  /**
   * A client whose requests appear to come from the given IP. The backend runs
   * with server.forward-headers-strategy=framework, so X-Forwarded-For sets the
   * request's remote address as a trusted proxy would.
   */
  static async fromIp(ip: string): Promise<ApiClient> {
    return ApiClient.create(undefined, { 'X-Forwarded-For': ip });
  }

  /** A client that presents only the given raw Cookie header (for replay tests). */
  static async withRawCookie(cookieHeader: string): Promise<ApiClient> {
    const ctx = await request.newContext({
      baseURL: env.backendUrl,
      extraHTTPHeaders: { Cookie: cookieHeader },
    });
    return new ApiClient(ctx);
  }

  async dispose(): Promise<void> {
    await this.ctx.dispose();
  }

  async cookie(name: string): Promise<string | undefined> {
    const state = await this.ctx.storageState();
    return state.cookies.find((c) => c.name === name)?.value;
  }

  async primeCsrf(): Promise<string> {
    let token = await this.cookie('XSRF-TOKEN');
    if (!token) {
      await this.ctx.get('/api/csrf');
      token = await this.cookie('XSRF-TOKEN');
    }
    if (!token) throw new Error('Backend did not issue an XSRF-TOKEN cookie from GET /api/csrf');
    return token;
  }

  async send(method: string, path: string, options: SendOptions = {}): Promise<ApiResult> {
    const upper = method.toUpperCase();
    const mutating = upper !== 'GET' && upper !== 'HEAD' && upper !== 'OPTIONS';
    const headers: Record<string, string> = { ...options.headers };
    if (options.csrf ?? mutating) {
      headers['X-XSRF-TOKEN'] = decodeURIComponent(await this.primeCsrf());
    }
    const response = await this.ctx.fetch(path, {
      method: upper,
      headers,
      data: options.data,
      failOnStatusCode: false,
      maxRedirects: 0,
    });
    return toResult(response);
  }

  get(path: string, options?: SendOptions) {
    return this.send('GET', path, options);
  }

  post(path: string, data?: unknown, options?: SendOptions) {
    return this.send('POST', path, { ...options, data });
  }

  // ---- Domain helpers -----------------------------------------------------

  register(user: { username: string; email: string; password: string }) {
    return this.post('/api/register', user);
  }

  login(username: string, password: string) {
    return this.post('/api/login', { username, password });
  }

  async loginOrThrow(username: string, password: string): Promise<void> {
    const res = await this.login(username, password);
    if (res.status !== 200) throw new Error(`Login for ${username} failed: ${res.status} ${res.text}`);
  }

  logout() {
    return this.post('/api/logout');
  }

  hello() {
    return this.get('/api/hello');
  }

  async listUsers(): Promise<AdminUserView[]> {
    const res = await this.get('/api/admin/users');
    if (res.status !== 200) throw new Error(`GET /api/admin/users failed: ${res.status} ${res.text}`);
    return res.json as AdminUserView[];
  }

  async userIdOf(username: string): Promise<number> {
    const user = (await this.listUsers()).find((u) => u.username === username);
    if (!user) throw new Error(`User ${username} not found in admin list`);
    return user.id;
  }
}

export interface AdminUserView {
  id: number;
  username: string;
  email: string;
  role: 'USER' | 'ADMIN';
  enabled: boolean;
  createdAt: string;
  [extra: string]: unknown;
}

export async function toResult(response: APIResponse): Promise<ApiResult> {
  const text = await response.text();
  let json: unknown = undefined;
  try {
    json = text ? JSON.parse(text) : undefined;
  } catch {
    json = undefined;
  }
  return {
    status: response.status(),
    headers: response.headers(),
    setCookies: response
      .headersArray()
      .filter((h) => h.name.toLowerCase() === 'set-cookie')
      .map((h) => h.value),
    text,
    json,
  };
}
