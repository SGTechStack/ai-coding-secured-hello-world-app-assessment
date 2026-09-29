import { expect, test } from '@playwright/test'
import { PASSWORD, usernameFor } from './fixtures.ts'

const NEW_PASSWORD = 'amber orchard lantern drifts'

test('a forced-change sign-in goes straight to the change-password page and is held there until it changes', async ({
  page,
}, testInfo) => {
  const username = usernameFor(testInfo, 'forced-change')
  await page.goto('/')
  await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible()
  await page.getByLabel('Username').fill(username)
  await page.getByLabel('Password').fill(PASSWORD)
  await page.getByRole('button', { name: 'Sign in' }).click()

  // The first gate: no greeting, straight to the change (ADR-046). BCrypt at cost 12 on a cold backend is slow.
  await expect(page.getByRole('heading', { name: 'Change password' })).toBeVisible({ timeout: 20_000 })
  await expect(page.getByText(/You must choose a new password before you can continue/)).toBeVisible()

  // The server refuses the greeting with PASSWORD_CHANGE_REQUIRED, and the SPA follows that authority back.
  await page.goto('/hello')
  await expect(page.getByRole('heading', { name: 'Change password' })).toBeVisible()

  await page.getByLabel('Current password').fill(PASSWORD)
  await page.getByLabel('New password').fill(NEW_PASSWORD)
  await page.getByRole('button', { name: 'Change password' }).click()

  await expect(page.getByRole('heading', { name: `Hello, ${username}` })).toBeVisible({ timeout: 20_000 })
})
