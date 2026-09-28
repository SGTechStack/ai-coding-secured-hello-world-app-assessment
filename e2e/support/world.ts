import { randomBytes } from 'node:crypto';
import type { ApiResult } from './api-client';

export interface TestUser {
  alias: string;
  username: string;
  email: string;
  /** The password the account currently has (updated when a step changes it). */
  password: string;
  /** The password the account was registered with. */
  originalPassword: string;
}

/** Short, collision-resistant suffix; usernames must match ^[a-zA-Z0-9_.-]+$. */
export function uniqueSuffix(): string {
  return `${Date.now().toString(36)}${randomBytes(3).toString('hex')}`;
}

export function newTestUser(alias: string): TestUser {
  const suffix = uniqueSuffix();
  const safeAlias = alias.replace(/[^a-zA-Z0-9]/g, '').toLowerCase() || 'user';
  // A distinctive password so "never logged" checks cannot match by accident.
  const password = `Pw-${safeAlias}-${randomBytes(6).toString('hex')}!`;
  return {
    alias,
    username: `e2e_${safeAlias}_${suffix}`,
    email: `e2e.${safeAlias}.${suffix}@example.test`,
    password,
    originalPassword: password,
  };
}

/**
 * Per-scenario state shared between steps. Scenario text refers to users by
 * alias ("alice"); the world maps aliases to generated unique accounts so
 * parallel scenarios never collide.
 */
export class World {
  readonly users = new Map<string, TestUser>();
  readonly saved = new Map<string, ApiResult>();
  last?: ApiResult;
  capturedSessionCookie?: string;
  /** Plaintext reset tokens by alias (see password-reset steps). */
  readonly resetTokens = new Map<string, string>();
  /** Registration payload of the most recent "via the API"/"through the form" attempt. */
  attemptedRegistration?: { username: string; email: string; password: string };
  /** JSESSIONID an actor held before logging in (session-fixation check). */
  anonymousSessionId?: string;
  /** Cached user ids, so {id:alias} still resolves after the user is deleted. */
  readonly ids = new Map<string, number>();
  /** Groups of users created together ("8 registered users named victim"). */
  readonly groups = new Map<string, string[]>();

  user(alias: string): TestUser {
    const user = this.users.get(alias);
    if (!user) throw new Error(`Unknown user alias "${alias}" — add a Given step that creates it`);
    return user;
  }

  lastResponse(): ApiResult {
    if (!this.last) throw new Error('No API response recorded yet');
    return this.last;
  }

  /**
   * Expands placeholders in feature-file text:
   *   {username:alice} {email:alice} {password:alice} {id:alice} {random}
   * `{id:...}` needs an async lookup, supplied by the caller.
   */
  async expand(text: string, idOf: (username: string) => Promise<number>): Promise<string> {
    let out = text
      .replace(/\{username:([^}]+)\}/g, (_, a) => this.user(a).username)
      .replace(/\{email:([^}]+)\}/g, (_, a) => this.user(a).email)
      .replace(/\{password:([^}]+)\}/g, (_, a) => this.user(a).password)
      .replace(/\{random\}/g, () => uniqueSuffix());
    for (const match of [...out.matchAll(/\{id:([^}]+)\}/g)]) {
      out = out.replace(match[0], String(await this.idOf(match[1], idOf)));
    }
    return out;
  }

  async idOf(alias: string, lookup: (username: string) => Promise<number>): Promise<number> {
    let id = this.ids.get(alias);
    if (id === undefined) {
      id = await lookup(this.user(alias).username);
      this.ids.set(alias, id);
    }
    return id;
  }
}
