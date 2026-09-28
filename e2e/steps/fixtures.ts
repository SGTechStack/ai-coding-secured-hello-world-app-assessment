import { test as base, createBdd } from 'playwright-bdd';
import { ApiClient } from '../support/api-client';
import { BackendLog } from '../support/backend-log';
import { env } from '../support/env';
import { H2Probe } from '../support/h2-probe';
import { World, newTestUser, type TestUser } from '../support/world';

/** Lazily creates one isolated API client (cookie jar) per named actor. */
export class Clients {
  private readonly pool = new Map<string, ApiClient>();

  async get(actor: string): Promise<ApiClient> {
    let client = this.pool.get(actor);
    if (!client) {
      client = await ApiClient.create();
      this.pool.set(actor, client);
    }
    return client;
  }

  /** A brand-new client, discarded after the scenario. */
  async fresh(): Promise<ApiClient> {
    const client = await ApiClient.create();
    this.pool.set(`__fresh_${this.pool.size}`, client);
    return client;
  }

  track(client: ApiClient): ApiClient {
    this.pool.set(`__tracked_${this.pool.size}`, client);
    return client;
  }

  async disposeAll(): Promise<void> {
    await Promise.all([...this.pool.values()].map((c) => c.dispose()));
  }
}

interface Fixtures {
  world: World;
  clients: Clients;
  /** Session of the bootstrap admin, created once by global-setup. */
  rootAdmin: ApiClient;
  db: H2Probe;
  backendLog: BackendLog;
  /** Registers a new account (optionally promoted to ADMIN by the root admin). */
  createUser: (alias: string, role?: 'USER' | 'ADMIN') => Promise<TestUser>;
}

export const test = base.extend<Fixtures>({
  world: async ({}, use) => {
    await use(new World());
  },

  clients: async ({}, use) => {
    const clients = new Clients();
    await use(clients);
    await clients.disposeAll();
  },

  rootAdmin: async ({}, use) => {
    const client = await ApiClient.create(env.rootAdminStateFile);
    await use(client);
    await client.dispose();
  },

  db: async ({}, use) => {
    const probe = await H2Probe.connect();
    await use(probe);
    await probe.dispose();
  },

  backendLog: async ({}, use) => {
    await use(new BackendLog());
  },

  createUser: async ({ world, rootAdmin }, use) => {
    await use(async (alias, role = 'USER') => {
      const user = newTestUser(alias);
      const registrar = await ApiClient.create();
      try {
        const res = await registrar.register(user);
        if (res.status !== 201) throw new Error(`Registering ${alias} failed: ${res.status} ${res.text}`);
      } finally {
        await registrar.dispose();
      }
      if (role === 'ADMIN') {
        const id = await rootAdmin.userIdOf(user.username);
        const res = await rootAdmin.send('PATCH', `/api/admin/users/${id}/role`, { data: { role: 'ADMIN' } });
        if (res.status !== 200) throw new Error(`Promoting ${alias} failed: ${res.status} ${res.text}`);
      }
      world.users.set(alias, user);
      return user;
    });
  },
});

export const { Given, When, Then, Before, After } = createBdd(test);

/** Resolves {id:alias} placeholders through the root admin's user list. */
export function idResolver(rootAdmin: ApiClient) {
  return (username: string) => rootAdmin.userIdOf(username);
}
