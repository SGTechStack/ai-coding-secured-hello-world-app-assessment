import path from 'node:path'
import { fileURLToPath } from 'node:url'

import { adminCredentials, expect, loginAs, logout, test } from '../../fixtures/auth'

/**
 * PRD acceptance-criteria evidence spec.
 *
 * Walks the user-facing PRD stories (prd/assessment-prd.md) through the REAL
 * SPA UI and saves one labelled full-page screenshot per acceptance criterion
 * into artifacts/acceptance/screenshots/. README2.md embeds these images.
 *
 * Selectors are user-visible (labels, roles, ids on the real form inputs) — no
 * API calls or token injection. The dev backend runs on in-memory H2 with a
 * fresh schema per lifecycle, and the AdminSeeder creates the `admin` persona.
 */

const here = path.dirname(fileURLToPath(import.meta.url))
// Stable, human-browsable location referenced by README2.md.
const SHOTS = path.resolve(here, '../../../artifacts/acceptance/screenshots')

/** A password that satisfies the >= 12 char strength policy. */
const STRONG_PASSWORD = 'CorrectHorse12!'
/** A password that fails the >= 12 char strength policy. */
const WEAK_PASSWORD = 'short1'

/** Unique-per-run suffix so re-runs never collide on username/email. */
const RUN = Date.now().toString(36)

async function shot(page: import('@playwright/test').Page, name: string) {
  await page.screenshot({ path: path.join(SHOTS, `${name}.png`), fullPage: true })
}

test.describe('PRD acceptance criteria — user-facing evidence', () => {
  // ---- Story 1: Registration --------------------------------------------
  test.describe('Story 1 — registration', () => {
    test('AC1.1 valid registration creates a USER account and lands on the protected page', async ({
      page,
    }) => {
      const username = `alice_${RUN}`
      await page.goto('/register')
      await page.locator('#username').fill(username)
      await page.locator('#email').fill(`${username}@example.com`)
      await page.locator('#password').fill(STRONG_PASSWORD)
      await shot(page, 'ac1.1-register-form-filled')
      await page.getByRole('button', { name: 'Create account' }).click()
      // A successful register signs the user in and routes to the protected page.
      await expect(page.getByText('Protected hello', { exact: true })).toBeVisible()
      await expect(page.getByText(`Signed in as ${username}`)).toBeVisible()
      await shot(page, 'ac1.1-registered-landing')
      await logout(page)
    })

    test('AC1.2 duplicate username/email is rejected with a validation error', async ({
      page,
    }) => {
      const username = `dupe_${RUN}`
      // First registration succeeds.
      await page.goto('/register')
      await page.locator('#username').fill(username)
      await page.locator('#email').fill(`${username}@example.com`)
      await page.locator('#password').fill(STRONG_PASSWORD)
      await page.getByRole('button', { name: 'Create account' }).click()
      await expect(page.getByText('Protected hello', { exact: true })).toBeVisible()
      await logout(page)

      // Second registration with the same identifiers is rejected.
      await page.goto('/register')
      await page.locator('#username').fill(username)
      await page.locator('#email').fill(`${username}@example.com`)
      await page.locator('#password').fill(STRONG_PASSWORD)
      await page.getByRole('button', { name: 'Create account' }).click()
      await expect(page.getByRole('alert')).toBeVisible()
      await shot(page, 'ac1.2-duplicate-rejected')
    })

    test('AC1.3 a password below the strength policy is rejected', async ({ page }) => {
      const username = `weak_${RUN}`
      await page.goto('/register')
      await page.locator('#username').fill(username)
      await page.locator('#email').fill(`${username}@example.com`)
      // Bypass the HTML minLength so the request reaches the server-side rule.
      await page.locator('#password').evaluate((el, val) => {
        const input = el as HTMLInputElement
        input.removeAttribute('minLength')
        input.value = val
        input.dispatchEvent(new Event('input', { bubbles: true }))
      }, WEAK_PASSWORD)
      await page.getByRole('button', { name: 'Create account' }).click()
      await expect(page.getByRole('alert')).toBeVisible()
      await shot(page, 'ac1.3-weak-password-rejected')
    })
  })

  // ---- Story 2: Login ----------------------------------------------------
  test.describe('Story 2 — login', () => {
    test('AC2.1 correct credentials create a session and reach protected content', async ({
      page,
    }) => {
      await page.goto('/login')
      await page.locator('#username').fill(adminCredentials.username)
      await page.locator('#password').fill(adminCredentials.password)
      await shot(page, 'ac2.1-login-form-filled')
      await page.getByRole('button', { name: 'Sign in' }).click()
      await expect(page.getByText('Protected hello', { exact: true })).toBeVisible()
      await shot(page, 'ac2.1-login-success')
      await logout(page)
    })

    test('AC2.2 wrong credentials show a generic error that does not reveal user existence', async ({
      page,
    }) => {
      await page.goto('/login')
      await page.locator('#username').fill(adminCredentials.username)
      await page.locator('#password').fill('definitely-the-wrong-password')
      await page.getByRole('button', { name: 'Sign in' }).click()
      const alert = page.getByRole('alert')
      await expect(alert).toBeVisible()
      // The message must not confirm/deny the username.
      await expect(alert).not.toHaveText(/no such user|unknown user|not found/i)
      await shot(page, 'ac2.2-generic-login-error')
    })
  })

  // ---- Story 4: Logout ---------------------------------------------------
  test.describe('Story 4 — logout', () => {
    test('AC4.1 logout ends the session and returns to the login form', async ({
      page,
    }) => {
      await loginAs(page, adminCredentials.username, adminCredentials.password)
      await shot(page, 'ac4.1-before-logout')
      await logout(page)
      await expect(page.getByRole('button', { name: 'Sign in' })).toBeVisible()
      await shot(page, 'ac4.1-after-logout')
    })

    test('AC4.2 a protected route is unauthenticated after logout', async ({ page }) => {
      await loginAs(page, adminCredentials.username, adminCredentials.password)
      await logout(page)
      // Navigating to the protected route now bounces to /login (401 → guard).
      await page.goto('/')
      await expect(page).toHaveURL(/\/login$/)
      await expect(page.getByRole('button', { name: 'Sign in' })).toBeVisible()
      await shot(page, 'ac4.2-protected-blocked-after-logout')
    })
  })

  // ---- Story 5: Protected content ---------------------------------------
  test.describe('Story 5 — protected greeting', () => {
    test('AC5.1 an authenticated session sees a personalized greeting', async ({
      page,
    }) => {
      await loginAs(page, adminCredentials.username, adminCredentials.password)
      const greeting = page.getByRole('status')
      await expect(greeting).toBeVisible()
      await expect(greeting).toHaveText(new RegExp(`Hello, ${adminCredentials.username}`))
      await shot(page, 'ac5.1-personalized-greeting')
      await logout(page)
    })

    test('AC5.2 no session cannot reach the protected page', async ({ page }) => {
      // Anonymous visit to the protected route redirects to /login.
      await page.goto('/')
      await expect(page).toHaveURL(/\/login$/)
      await expect(page.getByRole('button', { name: 'Sign in' })).toBeVisible()
      await shot(page, 'ac5.2-anonymous-redirected-to-login')
    })
  })

  // ---- Story 6: Password reset request ----------------------------------
  test.describe('Story 6 — password reset request', () => {
    test('AC6.1 the reset request returns a generic message for any email', async ({
      page,
    }) => {
      await page.goto('/forgot-password')
      await page.locator('#email').fill(`nobody_${RUN}@example.com`)
      await shot(page, 'ac6.1-forgot-password-form')
      await page.getByRole('button', { name: 'Send reset link' }).click()
      // The generic confirmation (role=status) appears regardless of existence.
      await expect(page.getByRole('status')).toBeVisible()
      await shot(page, 'ac6.1-generic-reset-confirmation')
    })
  })

  // ---- Story 8: Admin — list users --------------------------------------
  test.describe('Story 8 — admin user list', () => {
    test('AC8.1 an admin sees the user list with roles and status (no password hashes)', async ({
      page,
    }) => {
      await loginAs(page, adminCredentials.username, adminCredentials.password)
      await page.getByRole('link', { name: 'Admin panel' }).click()
      await expect(page.getByText('User management', { exact: true })).toBeVisible()
      await expect(page.getByRole('table')).toBeVisible()
      // The seeded admin row is present.
      await expect(page.getByRole('cell', { name: adminCredentials.username })).toBeVisible()
      // No password/hash column leaks into the table.
      await expect(page.getByText(/\$2[aby]\$/)).toHaveCount(0)
      await shot(page, 'ac8.1-admin-user-list')
    })

    test('AC8.2 a non-admin user has no route into the admin panel', async ({ page }) => {
      const username = `plainuser_${RUN}`
      // Register a fresh USER-role account.
      await page.goto('/register')
      await page.locator('#username').fill(username)
      await page.locator('#email').fill(`${username}@example.com`)
      await page.locator('#password').fill(STRONG_PASSWORD)
      await page.getByRole('button', { name: 'Create account' }).click()
      await expect(page.getByText('Protected hello', { exact: true })).toBeVisible()
      // No "Admin panel" link is offered to a USER.
      await expect(page.getByRole('link', { name: 'Admin panel' })).toHaveCount(0)
      await shot(page, 'ac8.2-user-no-admin-link')
      // Direct navigation to /admin bounces a non-admin back to the hello page.
      await page.goto('/admin')
      await expect(page).toHaveURL(/\/$/)
      await expect(page.getByText('Protected hello', { exact: true })).toBeVisible()
      await shot(page, 'ac8.2-user-admin-route-blocked')
      await logout(page)
    })
  })

  // ---- Stories 9-11: Admin self-action guard ----------------------------
  test.describe('Stories 9-11 — admin self-action guard', () => {
    test('AC9-11 an admin cannot disable, demote, or delete their own account', async ({
      page,
    }) => {
      await loginAs(page, adminCredentials.username, adminCredentials.password)
      await page.getByRole('link', { name: 'Admin panel' }).click()
      await expect(page.getByText('User management', { exact: true })).toBeVisible()
      // The acting admin's own row is marked "(you)" and its actions disabled.
      const selfRow = page.getByRole('row', { name: new RegExp(`${adminCredentials.username}`) })
      await expect(selfRow.getByText('(you)')).toBeVisible()
      await expect(selfRow.getByRole('button', { name: 'Disable' })).toBeDisabled()
      await expect(selfRow.getByRole('button', { name: 'Revoke admin' })).toBeDisabled()
      await expect(selfRow.getByRole('button', { name: 'Delete' })).toBeDisabled()
      await shot(page, 'ac9-11-self-action-guard')
      await logout(page)
    })
  })

  // ---- Story 12: Admin bootstrap ----------------------------------------
  test.describe('Story 12 — seeded admin bootstrap', () => {
    test('AC12.1 the seeded admin account can sign in on a fresh backend', async ({
      page,
    }) => {
      await page.goto('/login')
      await page.locator('#username').fill(adminCredentials.username)
      await page.locator('#password').fill(adminCredentials.password)
      await page.getByRole('button', { name: 'Sign in' }).click()
      await expect(page.getByText('Protected hello', { exact: true })).toBeVisible()
      await expect(page.getByText(`Signed in as ${adminCredentials.username}`)).toBeVisible()
      await expect(page.getByText('(ADMIN)')).toBeVisible()
      await shot(page, 'ac12.1-seeded-admin-login')
      await logout(page)
    })
  })
})
