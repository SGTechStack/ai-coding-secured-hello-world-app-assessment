import { createHmac } from 'node:crypto'
import { expect, type Page, type TestInfo } from '@playwright/test'

/** The fixture accounts `sg.securedhello.e2e.E2eBackend` creates: one per browser and test, one shared password. */
export const PASSWORD = 'e2e-password-correct-horse'

export type FixtureTest =
  | 'hello'
  | 'service-worker'
  | 'change-password'
  | 'reset'
  | 'forced-change'
  | 'golden-path'
  | 'disable-admin'
  | 'disable-user'
  | 'role-admin'
  | 'role-user'

/** The RFC 6238 code for `secret` at `at`: HMAC-SHA1, 30-second step, 6 digits. */
export function totp(secret: Buffer, at = Date.now()): string {
  const counter = Buffer.alloc(8)
  counter.writeBigUInt64BE(BigInt(Math.floor(at / 1000 / 30)))
  const hash = createHmac('sha1', secret).update(counter).digest()
  const offset = hash[hash.length - 1] & 0x0f
  return String((hash.readUInt32BE(offset) & 0x7fffffff) % 1_000_000).padStart(6, '0')
}

/** This browser's account for `test`, so parallel tests never displace each other's session. */
export function usernameFor(testInfo: TestInfo, test: FixtureTest): string {
  return `e2e-${testInfo.project.name}-${test}`
}

/** Signs in through the sign-in page and waits for the greeting. */
export async function signIn(page: Page, username: string, password = PASSWORD): Promise<void> {
  await page.goto('/')
  await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible()
  await page.getByLabel('Username').fill(username)
  await page.getByLabel('Password').fill(password)
  await page.getByRole('button', { name: 'Sign in' }).click()
  // The first sign-ins of a run meet a cold backend (and BCrypt at cost 12), so allow more than the default 5 s.
  await expect(page.getByRole('heading', { name: `Hello, ${username}` })).toBeVisible({ timeout: 20_000 })
}
