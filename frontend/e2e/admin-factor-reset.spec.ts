import { expect, type Page, test } from '@playwright/test'
import { PASSWORD, totp, usernameFor } from './fixtures.ts'

/** Both factor-reset administrators' enrolled secret, as `E2eBackend.ADMIN_TOTP_SECRET` stores it. */
const ADMIN_SECRET = Buffer.from('e2e-admin-disable-01', 'ascii')

/** Signs in with the password only; an enrolled administrator is then asked for a code. */
async function signInWithPassword(page: Page, username: string) {
  await page.goto('/')
  await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible()
  await page.getByLabel('Username').fill(username)
  await page.getByLabel('Password').fill(PASSWORD)
  await page.getByRole('button', { name: 'Sign in' }).click()
}

test("an administrator resets another administrator's authenticator app, who is signed out and sent to enrolment", async ({
  page,
  browser,
}, testInfo) => {
  const admin = usernameFor(testInfo, 'factor-reset-admin')
  const target = usernameFor(testInfo, 'factor-reset-target')

  // The target has lost their phone: signed in with the password, stuck at the challenge, in a browser of its own.
  const targetContext = await browser.newContext()
  const targetPage = await targetContext.newPage()
  await signInWithPassword(targetPage, target)
  await expect(targetPage.getByRole('heading', { name: 'TOTP Verification' })).toBeVisible({ timeout: 20_000 })

  // The administrator answers the challenge and opens the target from the list (ADR-021).
  await signInWithPassword(page, admin)
  await expect(page.getByRole('heading', { name: 'TOTP Verification' })).toBeVisible({ timeout: 20_000 })
  await page.getByLabel('Code from the app').fill(totp(ADMIN_SECRET))
  await page.keyboard.press('Enter')
  await expect(page).toHaveURL(/\/admin\/users$/)
  await page.getByRole('link', { name: target, exact: true }).click()

  await page.getByRole('button', { name: 'Reset authenticator app' }).click()
  await page.getByRole('button', { name: 'Confirm reset' }).click()
  await expect(page.getByRole('status').filter({ hasText: 'Authenticator app reset.' })).toBeVisible()

  // The reset ended the target's session (ADR-037), and at the next sign-in they are sent to enrolment (ADR-049).
  await targetPage.reload()
  await expect(targetPage.getByRole('heading', { name: 'Sign in' })).toBeVisible()
  await signInWithPassword(targetPage, target)
  await expect(targetPage.getByRole('heading', { name: 'Two-factor authentication' })).toBeVisible({ timeout: 20_000 })
  await expect(targetPage.getByRole('button', { name: 'Generate QR code' })).toBeVisible()
  await targetContext.close()
})
