import fs from 'node:fs';
import { expect } from '@playwright/test';
import { ApiClient, type AdminUserView } from '../support/api-client';
import { env } from '../support/env';
import type { BootstrapResult } from '../support/global-setup';
import { lit } from '../support/h2-probe';
import { newTestUser } from '../support/world';
import { Given, Then, When } from './fixtures';

// ---------------------------------------------------------------------------
// Admin user list (Story 8)
// ---------------------------------------------------------------------------

function userList(json: unknown): AdminUserView[] {
  expect(Array.isArray(json), 'user list should be a JSON array').toBe(true);
  return json as AdminUserView[];
}

Then(
  'the user list contains {string} with role {string} and enabled {word}',
  ({ world }, alias: string, role: string, enabled: string) => {
    const user = world.user(alias);
    const entry = userList(world.lastResponse().json).find((u) => u.username === user.username);
    expect(entry, `${alias} in user list`).toBeDefined();
    expect(entry!.email).toBe(user.email);
    expect(entry!.role).toBe(role);
    expect(entry!.enabled).toBe(enabled === 'true');
    expect(Number.isNaN(Date.parse(entry!.createdAt))).toBe(false);
  },
);

Then('every entry in the user list has exactly the fields {string}', ({ world }, fields: string) => {
  const expected = fields.split(',').map((f) => f.trim()).sort();
  for (const entry of userList(world.lastResponse().json)) {
    expect(Object.keys(entry).sort()).toEqual(expected);
  }
});

Then('the response body contains no password hash', ({ world }) => {
  const text = world.lastResponse().text;
  expect(text).not.toMatch(/\$2[aby]\$\d{2}\$/);
  expect(text).not.toMatch(/"[^"]*(password|hash)[^"]*"\s*:/i);
});

Then('the user list seen by {string} does not contain {string}', async ({ world, clients }, actor: string, alias: string) => {
  const users = await (await clients.get(actor)).listUsers();
  expect(users.map((u) => u.username)).not.toContain(world.user(alias).username);
});

// ---------------------------------------------------------------------------
// Bootstrap admin (Story 12)
// ---------------------------------------------------------------------------

Then('exactly one account exists with the configured bootstrap admin username', async ({ db }) => {
  expect(await db.userCount(`USERNAME = ${lit(env.bootstrapAdminUsername)}`)).toBe(1);
});

Then('the bootstrap admin account has role {string}', async ({ db }, role: string) => {
  expect((await db.userRow(env.bootstrapAdminUsername)).ROLE).toBe(role);
});

Then(
  'the bootstrap admin password is hashed with the same BCrypt scheme as a registered account',
  async ({ db }) => {
    const probe = newTestUser('hashprobe');
    const registrar = await ApiClient.create();
    try {
      expect((await registrar.register(probe)).status).toBe(201);
    } finally {
      await registrar.dispose();
    }
    const adminHash = (await db.userRow(env.bootstrapAdminUsername)).PASSWORD_HASH!;
    const userHash = (await db.userRow(probe.username)).PASSWORD_HASH!;
    // Same algorithm identifier and cost factor, e.g. "$2a$10$".
    expect(adminHash.slice(0, 7)).toBe(userHash.slice(0, 7));
    expect(adminHash).toMatch(/^\$2[aby]\$\d{2}\$/);
  },
);

Then('the configured bootstrap admin credentials were accepted on first login', () => {
  const result = JSON.parse(fs.readFileSync(env.bootstrapResultFile, 'utf8')) as BootstrapResult;
  expect(
    result.bootstrapCredentialsAccepted,
    'app.admin.username/password should log in against a freshly started backend ' +
      '(delete e2e/.auth and restart the backend if this run reused a backend from an older, unrecorded run)',
  ).toBe(true);
  expect(result.seededHashMatchesConfiguredPassword).toBe(true);
});

// Declared only so the @skip scenario still generates; never executed.
Given('an {string} user already exists', () => {});
When('the application restarts', () => {});

// ---------------------------------------------------------------------------
// Forced password change (mirrors the bootstrap admin's one-time
// forcePasswordChange flag on a disposable registered user — see the
// comment above the feature scenario that consumes these steps)
// ---------------------------------------------------------------------------

Given('a registered user {string} with a forced password change pending', async ({ createUser, db }, alias: string) => {
  const user = await createUser(alias);
  await db.exec(`UPDATE USERS SET FORCE_PASSWORD_CHANGE = TRUE WHERE USERNAME = ${lit(user.username)}`);
});

// ---------------------------------------------------------------------------
// Session fixation (NFR)
// ---------------------------------------------------------------------------

Given('{string} already holds an anonymous session cookie', async ({ world, clients }, alias: string) => {
  const client = await clients.get(alias);
  // Any request that touches the session gives an anonymous visitor a JSESSIONID.
  await client.get('/api/hello');
  await client.primeCsrf();
  world.anonymousSessionId = await client.cookie('JSESSIONID');
  expect(world.anonymousSessionId, 'anonymous JSESSIONID').toBeDefined();
});

Then('the session id of {string} differs from the anonymous one', async ({ world, clients }, alias: string) => {
  const current = await (await clients.get(alias)).cookie('JSESSIONID');
  expect(current).toBeDefined();
  expect(current).not.toBe(world.anonymousSessionId);
});

Then('the anonymous session cookie cannot access {string}', async ({ world, clients }, path: string) => {
  const client = clients.track(await ApiClient.withRawCookie(`JSESSIONID=${world.anonymousSessionId}`));
  expect((await client.get(path)).status).toBe(401);
});

// ---------------------------------------------------------------------------
// CORS (NFR)
// ---------------------------------------------------------------------------

async function preflight(clients: import('./fixtures').Clients, method: string, path: string, origin: string) {
  const client = await clients.fresh();
  const response = await client.ctx.fetch(path, {
    method: 'OPTIONS',
    headers: {
      Origin: origin,
      'Access-Control-Request-Method': method,
      'Access-Control-Request-Headers': 'content-type,x-xsrf-token',
    },
    failOnStatusCode: false,
  });
  return { status: response.status(), headers: response.headers(), setCookies: [], text: await response.text(), json: undefined };
}

When(
  'a CORS preflight for {word} {string} is sent from the frontend origin',
  async ({ world, clients }, method: string, path: string) => {
    world.last = await preflight(clients, method, path, env.frontendUrl);
  },
);

When(
  'a CORS preflight for {word} {string} is sent from origin {string}',
  async ({ world, clients }, method: string, path: string, origin: string) => {
    world.last = await preflight(clients, method, path, origin);
  },
);

Then('the response allows the frontend origin with credentials', ({ world }) => {
  const { status, headers } = world.lastResponse();
  expect(status).toBeLessThan(300);
  expect(headers['access-control-allow-origin']).toBe(env.frontendUrl);
  expect(headers['access-control-allow-credentials']).toBe('true');
  expect(headers['access-control-allow-methods'] ?? '').toMatch(/POST/);
});

Then('the response does not allow origin {string}', ({ world }, origin: string) => {
  const { headers } = world.lastResponse();
  expect(headers['access-control-allow-origin']).not.toBe(origin);
  expect(headers['access-control-allow-origin']).not.toBe('*');
});
