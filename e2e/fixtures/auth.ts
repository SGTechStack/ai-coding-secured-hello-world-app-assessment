import { expect, test as base, type Page } from '@playwright/test'

/**
 * Reusable foundation auth helpers.
 *
 * These drive the REAL login UI — no API calls, no token injection, no hidden
 * setup — so a story that consumes them still exercises the user-visible sign-in
 * path. They live in the foundation manifest (protected), which is what lets a
 * story spec import them without the verifier flagging setup mechanics.
 */

/** The dev-profile seeded admin persona. */
export const adminCredentials = {
  username: 'admin',
  password: 'admin-local-dev-password',
  role: 'ADMIN' as const,
}

/**
 * Sign in through the /login form and wait for the protected landing page.
 * Idempotent about the starting route: it always navigates to /login first,
 * and if an existing session already lands the SPA on the protected page it
 * reuses it rather than assuming a clean anonymous start.
 */
export async function loginAs(
  page: Page,
  username: string,
  password: string,
): Promise<void> {
  await page.goto('/login')

  // A live session redirects /login -> / (RedirectIfAuthed). If we already see
  // the protected landing page for this user, reuse it. The landing title is a
  // CardTitle (<div>), not a heading element, so match its exact text.
  const helloTitle = page.getByText('Protected hello', { exact: true })
  if (await helloTitle.isVisible().catch(() => false)) {
    return
  }

  await page.locator('#username').fill(username)
  await page.locator('#password').fill(password)
  await page.getByRole('button', { name: 'Sign in' }).click()

  await expect(helloTitle).toBeVisible()
}

/**
 * Sign out through the protected page's "Sign out" button and wait for the
 * login form to reappear.
 */
export async function logout(page: Page): Promise<void> {
  await page.getByRole('button', { name: 'Sign out' }).click()
  await expect(page.getByRole('button', { name: 'Sign in' })).toBeVisible()
}

/** Playwright test extended with the admin persona for convenience. */
export const test = base.extend<{ adminPersona: typeof adminCredentials }>({
  adminPersona: async ({}, use) => {
    await use(adminCredentials)
  },
})

export { expect }
