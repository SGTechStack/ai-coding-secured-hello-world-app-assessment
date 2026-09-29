import { expect, test } from '@playwright/test'
import { PASSWORD, signIn, totp, usernameFor } from './fixtures.ts'

/** The fixture administrator's enrolled secret, as `E2eBackend.ADMIN_TOTP_SECRET` stores it. */
const ADMIN_SECRET = Buffer.from('e2e-admin-disable-01', 'ascii')

test('an administrator disables a user, whose open session ends at its next request', async ({
  page,
  browser,
}, testInfo) => {
  const admin = usernameFor(testInfo, 'disable-admin')
  const target = usernameFor(testInfo, 'disable-user')

  // The target is signed in, in a browser of its own.
  const targetContext = await browser.newContext()
  const targetPage = await targetContext.newPage()
  await signIn(targetPage, target)

  // The administrator signs in and answers the challenge, then opens the target from the list (ADR-021).
  await page.goto('/')
  await page.getByLabel('Username').fill(admin)
  await page.getByLabel('Password').fill(PASSWORD)
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page.getByRole('heading', { name: 'TOTP Verification' })).toBeVisible({ timeout: 20_000 })
  await page.getByLabel('Code from the app').fill(totp(ADMIN_SECRET))
  await page.keyboard.press('Enter')
  await expect(page).toHaveURL(/\/admin\/users$/)
  await page.getByRole('link', { name: target, exact: true }).click()

  await page.getByRole('button', { name: 'Disable account' }).click()
  await expect(page.getByRole('status')).toHaveText('Account disabled. The user has been signed out.')
  await expect(page.getByText('Disabled', { exact: true })).toBeVisible()

  // The disable ended the target's session after commit: its next request is refused, and it is sent to sign in.
  await targetPage.reload()
  await expect(targetPage.getByRole('heading', { name: 'Sign in' })).toBeVisible()
  await targetContext.close()
})
