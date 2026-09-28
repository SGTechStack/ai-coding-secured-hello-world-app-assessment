import { request, type APIRequestContext } from '@playwright/test';
import { env } from './env';

export type Row = Record<string, string | null>;

/**
 * Test-only probe into the backend's in-memory H2 database, driven over
 * HTTP through the H2 web console that the dev profile exposes at
 * /h2-console (and that SecurityConfig denies outside the dev profile).
 *
 * It is the only black-box way to observe PRD data-model facts that the
 * REST API deliberately never exposes (BCrypt hashes, lockout counters,
 * reset-token hashes) and to fast-forward time (expire a lockout/token)
 * without waiting 15-30 minutes.
 */
export class H2Probe {
  private constructor(
    private readonly ctx: APIRequestContext,
    private readonly jsessionid: string,
  ) {}

  static async connect(): Promise<H2Probe> {
    const ctx = await request.newContext({ baseURL: env.backendUrl });
    const index = await ctx.get('/h2-console/');
    if (index.status() !== 200) {
      throw new Error(
        `H2 console unavailable (HTTP ${index.status()}). Run the backend with the dev profile.`,
      );
    }
    const match = (await index.text()).match(/jsessionid=([a-z0-9]+)/);
    if (!match) throw new Error('Could not find H2 console jsessionid');
    const jsessionid = match[1];
    const login = await ctx.post(`/h2-console/login.do?jsessionid=${jsessionid}`, {
      form: { driver: 'org.h2.Driver', url: env.h2JdbcUrl, user: env.h2User, password: env.h2Password },
    });
    const loginHtml = await login.text();
    if (login.status() !== 200 || /class="error"|Database .* not found/i.test(loginHtml)) {
      throw new Error(`H2 console login failed: ${loginHtml.slice(0, 500)}`);
    }
    return new H2Probe(ctx, jsessionid);
  }

  async dispose(): Promise<void> {
    await this.ctx.dispose();
  }

  /** Runs one SQL statement; returns result rows (empty for updates). */
  async query(sql: string): Promise<Row[]> {
    const res = await this.ctx.post(`/h2-console/query.do?jsessionid=${this.jsessionid}`, {
      form: { sql },
    });
    const html = await res.text();
    const error = html.match(/<div class="error">([\s\S]*?)<\/a>/);
    if (error) throw new Error(`H2 error for "${sql}": ${decode(stripTags(error[1]))}`);
    const table = html.match(/<table class="resultSet"[^>]*>([\s\S]*?)<\/table>/);
    if (!table) return [];
    const rows = [...table[1].matchAll(/<tr>([\s\S]*?)<\/tr>/g)].map((m) => m[1]);
    if (rows.length === 0) return [];
    const headers = [...rows[0].matchAll(/<th>([\s\S]*?)<\/th>/g)].map((m) => decode(stripTags(m[1])));
    // An UPDATE renders a one-column "Update count" table; treat as no rows.
    if (headers.length === 1 && /update count/i.test(headers[0])) return [];
    return rows.slice(1).map((r) => {
      const cells = [...r.matchAll(/<td>([\s\S]*?)<\/td>/g)].map((m) =>
        m[1] === '<i>null</i>' ? null : decode(stripTags(m[1])),
      );
      return Object.fromEntries(headers.map((h, i) => [h, cells[i] ?? null]));
    });
  }

  async userRow(username: string): Promise<Row> {
    const rows = await this.query(
      `SELECT ID, USERNAME, EMAIL, PASSWORD_HASH, ROLE, ENABLED, FAILED_LOGIN_ATTEMPTS, LOCKED_UNTIL, CREATED_AT ` +
        `FROM USERS WHERE USERNAME = ${lit(username)}`,
    );
    if (rows.length !== 1) throw new Error(`Expected exactly one USERS row for ${username}, got ${rows.length}`);
    return rows[0];
  }

  async userCount(where: string): Promise<number> {
    const rows = await this.query(`SELECT COUNT(*) AS N FROM USERS WHERE ${where}`);
    return Number(rows[0].N);
  }

  async resetTokensFor(username: string): Promise<Row[]> {
    return this.query(
      `SELECT T.ID, T.SELECTOR, T.TOKEN_HASH, T.USED_AT, T.EXPIRES_AT ` +
        `FROM PASSWORD_RESET_TOKENS T JOIN USERS U ON U.ID = T.USER_ID ` +
        `WHERE U.USERNAME = ${lit(username)} ORDER BY T.ID`,
    );
  }

  async exec(sql: string): Promise<void> {
    await this.query(sql);
  }
}

/** SQL string literal. */
export function lit(value: string): string {
  return `'${value.replace(/'/g, "''")}'`;
}

/**
 * Parses an H2 console TIMESTAMP WITH TIME ZONE cell such as
 * "2026-09-28 05:46:37.381678+00" to epoch millis. Done in JS because H2's
 * DATEDIFF ignores zone offsets when mixing CURRENT_TIMESTAMP (session zone)
 * with values the app stored in UTC.
 */
export function parseTimestamp(value: string | null): number | null {
  if (value === null) return null;
  const m = value.match(/^(\d{4}-\d{2}-\d{2}) (\d{2}:\d{2}:\d{2})(\.\d+)?([+-]\d{2})(?::?(\d{2}))?$/);
  if (!m) throw new Error(`Unrecognised H2 timestamp: ${value}`);
  const millis = m[3] ? m[3].slice(0, 4).padEnd(4, '0') : '';
  return Date.parse(`${m[1]}T${m[2]}${millis}${m[4]}:${m[5] ?? '00'}`);
}

/** A TIMESTAMP WITH TIME ZONE literal for an absolute point in time. */
export function tsLiteral(epochMillis: number): string {
  return `TIMESTAMP WITH TIME ZONE '${new Date(epochMillis).toISOString().replace('T', ' ').replace('Z', '+00:00')}'`;
}

function stripTags(s: string): string {
  return s.replace(/<[^>]*>/g, '').trim();
}

function decode(s: string): string {
  return s
    .replace(/&#(\d+);/g, (_, n) => String.fromCharCode(Number(n)))
    .replace(/&quot;/g, '"')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&nbsp;/g, ' ')
    .replace(/&amp;/g, '&');
}
