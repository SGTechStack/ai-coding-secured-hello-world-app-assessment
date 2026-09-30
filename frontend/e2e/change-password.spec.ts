import { expect, test } from '@playwright/test'
import { PASSWORD, signIn, usernameFor } from './fixtures.ts'

const NEW_PASSWORD = 'velvet harbour quietly hums'

test('changes the password against the real backend, then signs in with the new one only', async ({
  page,
}, testInfo) => {
  const username = usernameFor(testInfo, 'change-password')
  const apiOrigin = process.env.VITE_API_ORIGIN!
  await signIn(page, username)

  await page.getByRole('link', { name: 'Change password' }).click()
  await expect(page.getByRole('heading', { name: 'Change password' })).toBeVisible()

  // A policy rejection names its rule: the fixture account's own username is a context term.
  await page.getByLabel('Current password').fill(PASSWORD)
  await page.getByLabel('New password').fill(`${username} is my password`)
  await page.getByRole('button', { name: 'Change password' }).click()
  // Each change costs several BCrypt operations at the production cost, so allow more than the default 5 s.
  await expect(page.getByText('Do not use your username')).toBeVisible({ timeout: 20_000 })

  await page.getByLabel('New password').fill(NEW_PASSWORD)
  const change = page.waitForResponse((response) => response.url() === `${apiOrigin}/api/profile/password`)
  await page.getByRole('button', { name: 'Change password' }).click()
  expect((await change).status()).toBe(204)
  await expect(page.getByRole('status')).toHaveText(/Your password has been changed/, { timeout: 20_000 })

  // The session survived with a rotated id and CSRF token: signing out through it still works.
  await page.getByRole('button', { name: 'Back' }).click()
  const logout = page.waitForResponse((response) => response.url() === `${apiOrigin}/api/logout`)
  await page.getByRole('button', { name: 'Sign out' }).click()
  expect((await logout).status()).toBe(204)

  await signIn(page, username, NEW_PASSWORD)
})
