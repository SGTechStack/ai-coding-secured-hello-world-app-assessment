import { expect } from '@playwright/test';
import { ApiClient, type ApiResult } from '../support/api-client';
import { parseTimestamp } from '../support/h2-probe';
import { newTestUser } from '../support/world';
import { Given, Then, When, idResolver } from './fixtures';

// ---------------------------------------------------------------------------
// Accounts
// ---------------------------------------------------------------------------

Given('a registered user {string}', async ({ createUser }, alias: string) => {
  await createUser(alias);
});

Given('a registered admin {string}', async ({ createUser }, alias: string) => {
  await createUser(alias, 'ADMIN');
});

Given('{int} registered users named {string}', async ({ createUser, world }, count: number, name: string) => {
  const aliases = Array.from({ length: count }, (_, i) => `${name}${i + 1}`);
  for (const alias of aliases) await createUser(alias);
  world.groups.set(name, aliases);
});

Given('a visitor has chosen new account details {string}', ({ world }, alias: string) => {
  world.users.set(alias, newTestUser(alias));
});

Given(
  'a visitor has chosen new account details {string} with password {string}',
  ({ world }, alias: string, password: string) => {
    world.users.set(alias, { ...newTestUser(alias), password, originalPassword: password });
  },
);

Given('the id of {string} is known', async ({ world, rootAdmin }, alias: string) => {
  await world.idOf(alias, idResolver(rootAdmin));
});

// ---------------------------------------------------------------------------
// Registration via the API
// ---------------------------------------------------------------------------

async function registerViaApi(
  world: import('../support/world').World,
  payload: Record<string, unknown> & { username: string; email: string; password: string },
): Promise<void> {
  world.attemptedRegistration = { username: payload.username, email: payload.email, password: payload.password };
  const client = await ApiClient.create();
  try {
    world.last = await client.register(payload);
  } finally {
    await client.dispose();
  }
}

When('the visitor registers {string} via the API', async ({ world }, alias: string) => {
  const { username, email, password } = world.user(alias);
  await registerViaApi(world, { username, email, password });
});

When(
  'the visitor registers {string} via the API reusing the {word} of {string}',
  async ({ world }, alias: string, field: string, existingAlias: string) => {
    const { username, email, password } = world.user(alias);
    const existing = world.user(existingAlias);
    if (field !== 'username' && field !== 'email') throw new Error(`Unsupported field ${field}`);
    await registerViaApi(world, {
      username: field === 'username' ? existing.username : username,
      email: field === 'email' ? existing.email : email,
      password,
    });
  },
);

When(
  'the visitor registers {string} via the API with the extra field {string} set to {string}',
  async ({ world }, alias: string, field: string, value: string) => {
    const { username, email, password } = world.user(alias);
    await registerViaApi(world, { username, email, password, [field]: value });
  },
);

// ---------------------------------------------------------------------------
// Login / sessions via the API
// ---------------------------------------------------------------------------

Given('{string} is logged in', async ({ world, clients }, alias: string) => {
  const user = world.user(alias);
  await (await clients.get(alias)).loginOrThrow(user.username, user.password);
});

Given(
  '{string} is logged in on devices {string} and {string}',
  async ({ world, clients }, alias: string, d1: string, d2: string) => {
    const user = world.user(alias);
    for (const device of [d1, d2]) {
      await (await clients.get(`device:${device}`)).loginOrThrow(user.username, user.password);
    }
  },
);

When('{string} logs in with the correct password', async ({ world, clients }, alias: string) => {
  const user = world.user(alias);
  world.last = await (await clients.get(alias)).login(user.username, user.password);
});

When('someone logs in as {string} with a wrong password', async ({ world, clients }, alias: string) => {
  world.last = await (await clients.fresh()).login(world.user(alias).username, 'Definitely-Wrong-Password-1');
});

When('someone logs in with a username that is not registered', async ({ world, clients }) => {
  world.last = await (await clients.fresh()).login(newTestUser('ghost').username, 'Definitely-Wrong-Password-1');
});

async function failLogins(clients: import('./fixtures').Clients, username: string, times: number, client?: ApiClient) {
  const c = client ?? (await clients.fresh());
  for (let i = 0; i < times; i++) {
    const res = await c.login(username, `Wrong-Password-${i}-xyz`);
    if (res.status === 200) throw new Error('A wrong password unexpectedly logged in');
  }
}

Given('someone has failed to log in as {string} {int} times', async ({ world, clients }, alias: string, n: number) => {
  await failLogins(clients, world.user(alias).username, n);
});

When('someone fails to log in as {string} {int} times', async ({ world, clients }, alias: string, n: number) => {
  await failLogins(clients, world.user(alias).username, n);
});

Given('the account {string} is locked out', async ({ world, clients, db }, alias: string) => {
  const user = world.user(alias);
  await failLogins(clients, user.username, 5);
  const row = await db.userRow(user.username);
  expect(parseTimestamp(row.LOCKED_UNTIL) ?? 0, 'account should be locked after 5 failures').toBeGreaterThan(Date.now());
});

When(
  'a client at IP {string} fails to log in {int} times as each {string} user',
  async ({ world, clients }, ip: string, times: number, group: string) => {
    const client = clients.track(await ApiClient.fromIp(ip));
    for (const alias of world.groups.get(group) ?? []) {
      await failLogins(clients, world.user(alias).username, times, client);
    }
  },
);

When(
  'a client at IP {string} logs in as {string} with the correct password',
  async ({ world, clients }, ip: string, alias: string) => {
    const user = world.user(alias);
    world.last = await clients.track(await ApiClient.fromIp(ip)).login(user.username, user.password);
  },
);

Then('the login is rejected as throttled', ({ world }) => {
  const res = world.lastResponse();
  expect(res.status, `expected the throttled login to be refused, got ${res.status} ${res.text}`).not.toBe(200);
});

Then('{string} can access {string}', async ({ clients }, alias: string, path: string) => {
  expect((await (await clients.get(alias)).get(path)).status).toBe(200);
});

Then('{string} cannot access {string}', async ({ clients }, alias: string, path: string) => {
  expect((await (await clients.get(alias)).get(path)).status).toBe(401);
});

Then('device {string} cannot access {string}', async ({ clients }, device: string, path: string) => {
  expect((await (await clients.get(`device:${device}`)).get(path)).status).toBe(401);
});

Then('{string} can log in with the password {string}', async ({ world, clients }, alias: string, password: string) => {
  const res = await (await clients.fresh()).login(world.user(alias).username, password);
  expect(res.status, res.text).toBe(200);
});

Then('{string} can log in with their original password', async ({ world, clients }, alias: string) => {
  const user = world.user(alias);
  const res = await (await clients.fresh()).login(user.username, user.originalPassword);
  expect(res.status, res.text).toBe(200);
});

Then('{string} cannot log in with their original password', async ({ world, clients }, alias: string) => {
  const user = world.user(alias);
  const res = await (await clients.fresh()).login(user.username, user.originalPassword);
  expect(res.status).toBe(401);
});

Given('the session cookie of {string} has been captured', async ({ world, clients }, alias: string) => {
  const session = await (await clients.get(alias)).cookie('JSESSIONID');
  if (!session) throw new Error(`${alias} holds no JSESSIONID`);
  world.capturedSessionCookie = `JSESSIONID=${session}`;
});

When('the captured session cookie is replayed to {string}', async ({ world, clients }, path: string) => {
  if (!world.capturedSessionCookie) throw new Error('No session cookie captured');
  world.last = await clients.track(await ApiClient.withRawCookie(world.capturedSessionCookie)).get(path);
});

// ---------------------------------------------------------------------------
// Generic HTTP
// ---------------------------------------------------------------------------

async function sendAs(
  ctx: { world: import('../support/world').World; clients: import('./fixtures').Clients; rootAdmin: ApiClient },
  client: ApiClient,
  method: string,
  rawPath: string,
  docString: string | undefined,
  csrf: boolean | undefined,
): Promise<ApiResult> {
  const lookup = idResolver(ctx.rootAdmin);
  const path = await ctx.world.expand(rawPath, lookup);
  const body = docString === undefined ? '' : (await ctx.world.expand(docString, lookup)).trim();
  const bodyless = (method === 'GET' || method === 'DELETE') && (body === '' || body === '{}');
  const data = body === '' || bodyless ? undefined : JSON.parse(body);
  return client.send(method, path, { data, csrf });
}

When('{string} sends {word} {string}', async ({ world, clients, rootAdmin }, actor: string, method: string, path: string) => {
  world.last = await sendAs({ world, clients, rootAdmin }, await clients.get(actor), method, path, undefined, undefined);
});

When(
  '{string} sends {word} {string} with:',
  async ({ world, clients, rootAdmin }, actor: string, method: string, path: string, docString: string) => {
    world.last = await sendAs({ world, clients, rootAdmin }, await clients.get(actor), method, path, docString, undefined);
  },
);

When(
  '{string} sends {word} {string} without a CSRF token',
  async ({ world, clients, rootAdmin }, actor: string, method: string, path: string) => {
    world.last = await sendAs({ world, clients, rootAdmin }, await clients.get(actor), method, path, undefined, false);
  },
);

When(
  '{string} sends {word} {string} without a CSRF token with:',
  async ({ world, clients, rootAdmin }, actor: string, method: string, path: string, docString: string) => {
    world.last = await sendAs({ world, clients, rootAdmin }, await clients.get(actor), method, path, docString, false);
  },
);

When('an anonymous client sends {word} {string}', async ({ world, clients, rootAdmin }, method: string, path: string) => {
  world.last = await sendAs({ world, clients, rootAdmin }, await clients.fresh(), method, path, undefined, undefined);
});

When(
  'a client with the session cookie {string} sends {word} {string}',
  async ({ world, clients, rootAdmin }, cookie: string, method: string, path: string) => {
    const client = clients.track(await ApiClient.withRawCookie(cookie));
    world.last = await sendAs({ world, clients, rootAdmin }, client, method, path, undefined, undefined);
  },
);

When('the response is remembered as {string}', ({ world }, name: string) => {
  world.saved.set(name, world.lastResponse());
});

Then('the response status is {int}', ({ world }, status: number) => {
  const res = world.lastResponse();
  expect(res.status, `response body: ${res.text}`).toBe(status);
});

function jsonPath(json: unknown, path: string): unknown {
  return path.split('.').reduce<unknown>((acc, key) => (acc as Record<string, unknown> | undefined)?.[key], json);
}

Then('the response JSON field {string} is {string}', async ({ world, rootAdmin }, path: string, expected: string) => {
  const value = jsonPath(world.lastResponse().json, path);
  expect(value).toBe(await world.expand(expected, idResolver(rootAdmin)));
});

Then('the response error message is {string}', ({ world }, message: string) => {
  expect(jsonPath(world.lastResponse().json, 'error')).toBe(message);
});

Then(
  'the remembered responses {string} and {string} are identical',
  ({ world }, a: string, b: string) => {
    const ra = world.saved.get(a)!;
    const rb = world.saved.get(b)!;
    expect(rb.status).toBe(ra.status);
    expect(rb.text).toBe(ra.text);
    expect(rb.headers['content-type']).toBe(ra.headers['content-type']);
  },
);

Then(
  'the remembered response {string} has status {int} and JSON field {string} equal to {string}',
  ({ world }, name: string, status: number, field: string, expected: string) => {
    const res = world.saved.get(name)!;
    expect(res.status).toBe(status);
    expect(jsonPath(res.json, field)).toBe(expected);
  },
);

// ---------------------------------------------------------------------------
// Cookies on the last response
// ---------------------------------------------------------------------------

function setCookie(res: ApiResult, name: string): string | undefined {
  return res.setCookies.flatMap((h) => h.split('\n')).find((c) => c.startsWith(`${name}=`));
}

Then('the response sets an HttpOnly {string} cookie', ({ world }, name: string) => {
  const cookie = setCookie(world.lastResponse(), name);
  expect(cookie, `Set-Cookie for ${name}`).toBeDefined();
  expect(cookie).toMatch(/;\s*HttpOnly/i);
});

Then('the {string} cookie set by the response has a SameSite attribute', ({ world }, name: string) => {
  expect(setCookie(world.lastResponse(), name)).toMatch(/;\s*SameSite=(Lax|Strict)/i);
});

Then('the {string} cookie set by the response is Secure', ({ world }, name: string) => {
  expect(setCookie(world.lastResponse(), name)).toMatch(/;\s*Secure/i);
});

Then('the response clears the {string} cookie', ({ world }, name: string) => {
  const cookie = setCookie(world.lastResponse(), name);
  expect(cookie, `expected a Set-Cookie header expiring ${name}`).toBeDefined();
  const expired =
    /;\s*Max-Age=0/i.test(cookie!) ||
    (/;\s*Expires=([^;]+)/i.test(cookie!) && Date.parse(cookie!.match(/;\s*Expires=([^;]+)/i)![1]) < Date.now());
  expect(expired, cookie).toBe(true);
});
