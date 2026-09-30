import { expect, test } from '@playwright/test'
import { PASSWORD, signIn, totp, usernameFor } from './fixtures.ts'

/** The fixture administrator's enrolled secret, as `E2eBackend.ADMIN_TOTP_SECRET` stores it. */
const ADMIN_SECRET = Buffer.from('e2e-admin-disable-01', 'ascii')
const STEP_SECONDS = 30_000

test('an expired factor opens the step-up challenge, and one code replays the queued disable', async ({
  page,
  browser,
  request,
}, testInfo) => {
  const admin = usernameFor(testInfo, 'step-up-admin')
  const target = usernameFor(testInfo, 'step-up-user')

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
  await expect(page.getByRole('button', { name: 'Disable account' })).toBeVisible()

  // Eleven minutes pass for this session's factor: past the mutation window, still good for reads.
  const apiOrigin = process.env.VITE_API_ORIGIN ?? ''
  const aged = await request.post(`${apiOrigin}/e2e/factor-age?username=${encodeURIComponent(admin)}`)
  expect(aged.status()).toBe(204)

  // The disable is refused for a stale factor and queued; the page stays put and the challenge opens.
  await page.getByRole('button', { name: 'Disable account' }).click()
  const dialog = page.getByRole('dialog', { name: 'TOTP Verification' })
  await expect(dialog).toBeVisible()
  await expect(page).toHaveURL(/\/admin\/users\/[0-9a-f-]+$/)
  await expect(dialog.getByLabel('Code from the app')).toBeFocused()

  // The next step's code: the sign-in's code can never verify again (RFC 6238 §5.2).
  await dialog.getByLabel('Code from the app').fill(totp(ADMIN_SECRET, Date.now() + STEP_SECONDS))
  await dialog.getByRole('button', { name: 'Verify' }).click()

  // One code: the queued disable is replayed with the rotated session's token and goes through.
  await expect(dialog).toBeHidden()
  await expect(page.getByRole('status')).toHaveText('Account disabled. The user has been signed out.')
  await expect(page.getByText('Disabled', { exact: true })).toBeVisible()

  await targetPage.reload()
  await expect(targetPage.getByRole('heading', { name: 'Sign in' })).toBeVisible()
  await targetContext.close()
})
