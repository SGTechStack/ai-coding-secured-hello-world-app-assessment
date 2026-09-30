import { expect, test } from '@playwright/test'
import { PASSWORD, signIn, totp, usernameFor } from './fixtures.ts'

/** The fixture administrator's enrolled secret, as `E2eBackend.ADMIN_TOTP_SECRET` stores it. */
const ADMIN_SECRET = Buffer.from('e2e-admin-disable-01', 'ascii')

test('an administrator promotes a user, ending its session, then deletes it for good', async ({
  page,
  browser,
}, testInfo) => {
  const admin = usernameFor(testInfo, 'role-admin')
  const target = usernameFor(testInfo, 'role-user')

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

  // The role change is confirmed in a dialog, and ends the target's session so no session keeps the old role.
  await page.getByRole('button', { name: 'Make administrator' }).click()
  const roleDialog = page.getByRole('alertdialog', { name: `Make ${target} an administrator?` })
  await roleDialog.getByRole('button', { name: 'Change role' }).click()
  await expect(page.getByRole('status')).toHaveText('Role changed to administrator. The user has been signed out.')
  await expect(page.getByText('ADMIN', { exact: true })).toBeVisible()
  await targetPage.reload()
  await expect(targetPage.getByRole('heading', { name: 'Sign in' })).toBeVisible()
  await targetContext.close()

  // The delete, by keyboard alone: the dialog opens on Cancel, Tab reaches Delete, Enter confirms.
  await page.getByRole('button', { name: 'Delete account' }).focus()
  await page.keyboard.press('Enter')
  const dialog = page.getByRole('alertdialog', { name: `Delete ${target}?` })
  await expect(dialog.getByRole('button', { name: 'Cancel' })).toBeFocused()
  await page.keyboard.press('Tab')
  await expect(dialog.getByRole('button', { name: 'Delete' })).toBeFocused()
  await page.keyboard.press('Enter')
  await expect(page.getByRole('status')).toHaveText(/^Account deleted\./)

  await page.getByRole('link', { name: 'Back to the user list' }).click()
  await expect(page.getByRole('link', { name: admin, exact: true })).toBeVisible()
  await expect(page.getByRole('link', { name: target, exact: true })).toHaveCount(0)

  // The tombstone keeps the username: registering it again is refused as taken (ADR-044).
  const registration = await browser.newPage()
  await registration.goto('/register')
  await registration.getByLabel('Username').fill(target)
  await registration.getByLabel('Email address').fill(`${target}-again@example.test`)
  await registration.getByRole('button', { name: 'Register' }).click()
  await expect(registration.getByText('That username is not available. Choose another.')).toBeVisible({
    timeout: 20_000,
  })
  await registration.close()
})
