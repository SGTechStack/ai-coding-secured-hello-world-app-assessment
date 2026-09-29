import { expect, type Page, type TestInfo } from '@playwright/test'

/** The fixture accounts `sg.securedhello.e2e.E2eBackend` creates: one per browser and test, one shared password. */
export const PASSWORD = 'e2e-password-correct-horse'

export type FixtureTest = 'hello' | 'service-worker' | 'change-password'

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
