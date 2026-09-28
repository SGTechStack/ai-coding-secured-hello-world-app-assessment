import { expect } from '@playwright/test';
import bcrypt from 'bcryptjs';
import { lit, parseTimestamp, tsLiteral } from '../support/h2-probe';
import { Given, Then, When, idResolver } from './fixtures';

/** BCrypt modular-crypt format: $2a$/$2b$/$2y$, two-digit cost, 53 chars of salt+hash. */
const BCRYPT = /^\$2[aby]\$\d{2}\$[./A-Za-z0-9]{53}$/;

// ---------------------------------------------------------------------------
// Persisted account state (read through the dev-profile H2 console)
// ---------------------------------------------------------------------------

Then(
  'the account {string} exists with role {string} and enabled status {string}',
  async ({ world, db }, alias: string, role: string, enabled: string) => {
    const row = await db.userRow(world.user(alias).username);
    expect(row.ROLE).toBe(role);
    expect(row.ENABLED).toBe(enabled);
  },
);

Then('the stored password of {string} is a BCrypt hash of their password', async ({ world, db }, alias: string) => {
  const user = world.user(alias);
  const hash = (await db.userRow(user.username)).PASSWORD_HASH ?? '';
  expect(hash).toMatch(BCRYPT);
  expect(hash).not.toContain(user.password);
  expect(await bcrypt.compare(user.password, hash)).toBe(true);
});

Then('{string} has {int} failed login attempts recorded', async ({ world, db }, alias: string, n: number) => {
  expect(Number((await db.userRow(world.user(alias).username)).FAILED_LOGIN_ATTEMPTS)).toBe(n);
});

Then('the account {string} is not locked', async ({ world, db }, alias: string) => {
  const row = await db.userRow(world.user(alias).username);
  const lockedUntil = parseTimestamp(row.LOCKED_UNTIL);
  expect(lockedUntil === null || lockedUntil <= Date.now(), `LOCKED_UNTIL=${row.LOCKED_UNTIL}`).toBe(true);
});

Then('none of the {string} accounts are locked', async ({ world, db }, group: string) => {
  for (const alias of world.groups.get(group) ?? []) {
    const row = await db.userRow(world.user(alias).username);
    expect(row.LOCKED_UNTIL, `${alias} should not be locked`).toBeNull();
  }
});

Then('the account {string} is locked for about {int} minutes', async ({ world, db }, alias: string, minutes: number) => {
  const row = await db.userRow(world.user(alias).username);
  const lockedFor = ((parseTimestamp(row.LOCKED_UNTIL) ?? 0) - Date.now()) / 1000;
  expect(lockedFor).toBeGreaterThan(minutes * 60 - 60);
  expect(lockedFor).toBeLessThanOrEqual(minutes * 60 + 5);
});

Given('the lockout of {string} has expired', async ({ world, db }, alias: string) => {
  // Fast-forward: the cooldown is 15 minutes, too long to wait for in a test.
  await db.exec(
    `UPDATE USERS SET LOCKED_UNTIL = ${tsLiteral(Date.now() - 1_000)} WHERE USERNAME = ${lit(world.user(alias).username)}`,
  );
});

Then('no database record exists for {string}', async ({ world, db }, alias: string) => {
  expect(await db.userCount(`USERNAME = ${lit(world.user(alias).username)}`)).toBe(0);
});

Then('no account was created from the rejected registration', async ({ world, db }) => {
  const attempt = world.attemptedRegistration;
  if (!attempt) throw new Error('No registration attempt recorded');
  // Nothing with exactly the attempted pair exists, and neither identifier gained a second row.
  expect(await db.userCount(`USERNAME = ${lit(attempt.username)} AND EMAIL = ${lit(attempt.email)}`)).toBe(0);
  expect(await db.userCount(`USERNAME = ${lit(attempt.username)}`)).toBeLessThanOrEqual(1);
  expect(await db.userCount(`EMAIL = ${lit(attempt.email)}`)).toBeLessThanOrEqual(1);
});

// ---------------------------------------------------------------------------
// Backend structured log
// ---------------------------------------------------------------------------

Then('the backend log records {string}', async ({ world, rootAdmin, backendLog }, text: string) => {
  const expected = await world.expand(text, idResolver(rootAdmin));
  // Logback writes asynchronously enough that a short poll avoids flakiness.
  await expect
    .poll(() => backendLog.messages().some((m) => m.includes(expected)), {
      message: `backend log should contain "${expected}"`,
      timeout: 5_000,
    })
    .toBe(true);
});

Then('the plaintext password of {string} does not appear in the backend log', ({ world, backendLog }, alias: string) => {
  const user = world.user(alias);
  const log = backendLog.sinceStart();
  expect(log).not.toContain(user.originalPassword);
  expect(log).not.toContain(user.password);
});

Then('the text {string} does not appear in the backend log', ({ backendLog }, text: string) => {
  expect(backendLog.sinceStart()).not.toContain(text);
});

Then('the backend log is structured JSON', ({ backendLog }) => {
  const lines = backendLog.sinceStart().split('\n').filter(Boolean);
  expect(lines.length).toBeGreaterThan(0);
  for (const line of lines) {
    const entry = JSON.parse(line) as Record<string, unknown>;
    expect(entry).toHaveProperty('@timestamp');
    expect(entry).toHaveProperty('message');
  }
});

// ---------------------------------------------------------------------------
// Password reset tokens
// ---------------------------------------------------------------------------

async function latestToken(db: import('../support/h2-probe').H2Probe, username: string) {
  const rows = await db.resetTokensFor(username);
  if (rows.length === 0) throw new Error(`No reset token for ${username}`);
  return rows[rows.length - 1];
}

Then('exactly {int} reset token exists for {string}', async ({ world, db }, n: number, alias: string) => {
  expect(await db.resetTokensFor(world.user(alias).username)).toHaveLength(n);
});

Then('the latest reset token of {string} is unused', async ({ world, db }, alias: string) => {
  expect((await latestToken(db, world.user(alias).username)).USED_AT).toBeNull();
});

Then('the latest reset token of {string} is marked used', async ({ world, db }, alias: string) => {
  expect((await latestToken(db, world.user(alias).username)).USED_AT).not.toBeNull();
});

Then('the latest reset token of {string} is stored as a BCrypt hash', async ({ world, db }, alias: string) => {
  const token = await latestToken(db, world.user(alias).username);
  expect(token.TOKEN_HASH).toMatch(BCRYPT);
  // The only plaintext part persisted is the non-secret lookup selector.
  expect(token.TOKEN_HASH).not.toContain(token.SELECTOR!);
});

Then(
  'the latest reset token of {string} expires in between {int} and {int} minutes',
  async ({ world, db }, alias: string, min: number, max: number) => {
    const token = await latestToken(db, world.user(alias).username);
    const seconds = ((parseTimestamp(token.EXPIRES_AT) ?? 0) - Date.now()) / 1000;
    expect(seconds).toBeGreaterThanOrEqual(min * 60 - 60);
    expect(seconds).toBeLessThanOrEqual(max * 60);
  },
);

Given('the latest reset token of {string} has expired', async ({ world, db }, alias: string) => {
  const token = await latestToken(db, world.user(alias).username);
  await db.exec(
    `UPDATE PASSWORD_RESET_TOKENS SET EXPIRES_AT = ${tsLiteral(Date.now() - 60_000)} WHERE ID = ${Number(token.ID)}`,
  );
});

Then(
  'the latest reset token secret of {string} does not appear in the backend log',
  async ({ world, db, backendLog }, alias: string) => {
    const token = await latestToken(db, world.user(alias).username);
    const log = backendLog.sinceStart();
    expect(log).not.toContain(token.SELECTOR!);
    expect(log).not.toContain('reset-password?token=');
    const known = world.resetTokens.get(alias);
    if (known) expect(log).not.toContain(known);
  },
);

/**
 * Drives the real request flow, then makes the token's secret known to the
 * test. The stub EmailService keeps the link in memory only (it is never
 * logged or exposed over HTTP, by design), so the verifier half of the
 * issued token is unrecoverable black-box. We keep the app-issued row and
 * selector and replace only its BCrypt verifier hash with one we can
 * reproduce: the backend then validates it exactly like a mailed token.
 */
Given(
  '{string} has requested a password reset and received the reset link',
  async ({ world, clients, db }, alias: string) => {
    const user = world.user(alias);
    const res = await (await clients.fresh()).post('/api/password-reset/request', { email: user.email });
    expect(res.status).toBe(200);
    const token = await latestToken(db, user.username);
    const verifier = `e2e-verifier-${Math.random().toString(36).slice(2)}${Date.now().toString(36)}`;
    const hash = await bcrypt.hash(verifier, 10);
    await db.exec(`UPDATE PASSWORD_RESET_TOKENS SET TOKEN_HASH = ${lit(hash)} WHERE ID = ${Number(token.ID)}`);
    world.resetTokens.set(alias, `${token.SELECTOR}.${verifier}`);
  },
);

When(
  'an anonymous client requests a password reset for the email of {string}',
  async ({ world, clients }, alias: string) => {
    world.last = await (await clients.fresh()).post('/api/password-reset/request', { email: world.user(alias).email });
  },
);

When('an anonymous client requests a password reset for an unregistered email', async ({ world, clients }) => {
  world.last = await (await clients.fresh()).post('/api/password-reset/request', {
    email: `nobody.${Date.now()}@example.test`,
  });
});

When(
  'an anonymous client confirms the reset of {string} with the new password {string}',
  async ({ world, clients }, alias: string, newPassword: string) => {
    const token = world.resetTokens.get(alias);
    if (!token) throw new Error(`No reset token known for ${alias}`);
    world.last = await (await clients.fresh()).post('/api/password-reset/confirm', { token, newPassword });
    if (world.last.status === 200) world.user(alias).password = newPassword;
  },
);
