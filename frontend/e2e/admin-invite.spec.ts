import { expect, test } from '@playwright/test'
import { PASSWORD, signIn, totp, usernameFor } from './fixtures.ts'

/** The fixture administrator's enrolled secret, as `E2eBackend.ADMIN_TOTP_SECRET` stores it. */
const ADMIN_SECRET = Buffer.from('e2e-admin-disable-01', 'ascii')
const INVITEE_PASSWORD = 'lantern orchard quietly drifts'

test('an administrator invites a user, whose one-time link activates the account', async ({
  page,
  browser,
}, testInfo) => {
  const admin = usernameFor(testInfo, 'invite-admin')
  // A fresh name per attempt, so a retry never meets its own earlier invite.
  const invitee = `e2e-${testInfo.project.name}-inv-${Date.now().toString(36)}`

  // The administrator signs in and answers the challenge (ADR-021), then opens the invite form from the list.
  await page.goto('/')
  await page.getByLabel('Username').fill(admin)
  await page.getByLabel('Password').fill(PASSWORD)
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page.getByRole('heading', { name: 'TOTP Verification' })).toBeVisible({ timeout: 20_000 })
  await page.getByLabel('Code from the app').fill(totp(ADMIN_SECRET))
  await page.keyboard.press('Enter')
  await expect(page).toHaveURL(/\/admin\/users$/)
  await page.getByRole('link', { name: 'Invite a user' }).click()

  await page.getByLabel('Username').fill(invitee)
  await page.getByLabel('Email address').fill(`${invitee}@example.test`)
  await page.getByRole('button', { name: 'Create invitation' }).click()

  // The activation link is shown once, on the SPA's origin, with the token in the fragment (ADR-006; R-FE-007).
  await expect(page.getByRole('heading', { name: `Invitation for ${invitee}` })).toBeVisible()
  const link = await page.getByText(/\/activate#token=/).textContent()
  expect(link).toMatch(new RegExp(`^${String(testInfo.project.use.baseURL)}/activate#token=[A-Za-z0-9_-]{43}$`))

  // The invitee, in a browser of their own, sets their own password from the link and signs in.
  const inviteeContext = await browser.newContext()
  const inviteePage = await inviteeContext.newPage()
  await inviteePage.goto(link!)
  await expect(inviteePage.getByRole('heading', { name: 'Activate your account' })).toBeVisible()
  await inviteePage.getByLabel('Choose a password').fill(INVITEE_PASSWORD)
  await inviteePage.getByRole('button', { name: 'Activate' }).click()
  await expect(inviteePage.getByRole('status')).toHaveText('Your password is set. You can now sign in.', {
    timeout: 20_000,
  })
  await signIn(inviteePage, invitee, INVITEE_PASSWORD)
  await inviteeContext.close()
})
