import { expect, type Page, test } from '@playwright/test'

// The dev-only demo accounts the E2E backend (dev profile) seeds. They are shared by every browser, and one session per
// account plus replay rejection of a used code would make two browsers race, so this runs on Chromium only.
test.skip(({ browserName }) => browserName !== 'chromium', 'the demo accounts are shared by every browser')

/** Opens sign-in, fills the form from the demo panel's entry for `username`, and returns that entry. */
async function fillFromPanel(page: Page, username: string) {
  await page.goto('/')
  const panel = page.getByRole('region', { name: 'Demo accounts' })
  await expect(panel).toBeVisible({ timeout: 20_000 })
  await panel.getByRole('button', { name: `Fill in ${username}` }).click()
  await expect(page.getByLabel('Username')).toHaveValue(username)
  return panel
}

test('the demo user signs in from the sign-in page panel', async ({ page }) => {
  await fillFromPanel(page, 'demo-user')
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()

  await expect(page.getByRole('heading', { name: 'Hello, demo-user' })).toBeVisible({ timeout: 20_000 })
})

test('the demo administrator signs in with the panel password and code and reaches the user list', async ({ page }) => {
  const panel = await fillFromPanel(page, 'demo-admin')
  const code = (await panel.locator('code[aria-live]').textContent())?.trim() ?? ''
  expect(code).toMatch(/^\d{6}$/)
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()

  // Pre-enrolled, with no forced change: straight to the challenge. The server accepts one step of skew, so the code
  // read a moment ago still verifies if its step has just ended.
  await expect(page.getByRole('heading', { name: 'TOTP Verification' })).toBeVisible({ timeout: 20_000 })
  await page.getByLabel('Code from the app').fill(code)
  await page.keyboard.press('Enter')

  await expect(page).toHaveURL(/\/admin\/users$/)
  await expect(page.getByRole('table').getByRole('link', { name: 'demo-user', exact: true })).toBeVisible()
})
