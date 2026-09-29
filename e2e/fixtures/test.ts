import crypto from 'node:crypto'
import { test as base, expect, request } from '@playwright/test'
import { readLifecycle, type Lifecycle } from './lifecycle'

export interface Persona {
  username: string
  email: string
  password: string
}

interface TestFixtures {
  /** Creates a fresh, enabled USER account through the public registration API. */
  newUser: (overrides?: Partial<Persona>) => Promise<Persona>
}

interface WorkerFixtures {
  lifecycle: Lifecycle
  /** The ADMIN seeded by the lifecycle's admin bootstrap. */
  admin: Persona
}

export const test = base.extend<TestFixtures, WorkerFixtures>({
  lifecycle: [
    async ({}, use) => {
      await use(readLifecycle())
    },
    { scope: 'worker' },
  ],

  admin: [
    async ({ lifecycle }, use) => {
      await use(lifecycle.admin)
    },
    { scope: 'worker' },
  ],

  newUser: async ({ lifecycle }, use) => {
    const api = await request.newContext({ baseURL: lifecycle.apiUrl })
    await use(async (overrides = {}) => {
      // Unique per call, so a rerun or worker restart never collides with earlier data.
      const suffix = `${Date.now().toString(36)}${crypto.randomBytes(3).toString('hex')}`
      const persona: Persona = {
        username: `u_${suffix}`,
        email: `u_${suffix}@example.com`,
        password: `E2e-Passphrase-${suffix}`,
        ...overrides,
      }
      const csrf = await api.get('/api/auth/csrf')
      expect(csrf.ok(), 'CSRF token for registration').toBeTruthy()
      const { headerName, token } = (await csrf.json()) as { headerName: string; token: string }
      const created = await api.post('/api/auth/register', { headers: { [headerName]: token }, data: persona })
      expect(created.status(), `registering ${persona.username}`).toBe(201)
      return persona
    })
    await api.dispose()
  },
})

export { expect }
