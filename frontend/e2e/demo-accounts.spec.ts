import { expect, type Page, test } from '@playwright/test'
import { usernameFor } from './fixtures.ts'

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
  const panel = await fillFromPanel(page, 'demo-user')
  // Both accounts are listed, each with its seeded email for the forgot-password flow.
  await expect(panel.getByText('demo-user@demo.invalid', { exact: true })).toBeVisible()
  await expect(panel.getByText('demo-admin@demo.invalid', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()

  await expect(page.getByRole('heading', { name: 'Hello, demo-user' })).toBeVisible({ timeout: 20_000 })
})

test('the demo administrator signs in from the panel, then answers a step-up from the demo code hint', async ({
  page,
  request,
}, testInfo) => {
  // Up to one 30-second step is spent waiting out the sign-in's code before the step-up can use the next one.
  test.setTimeout(120_000)
  // A fixture account of its own as the disable target, so the demo user another test signs in as is never disabled.
  const target = usernameFor(testInfo, 'demo-code-user')
  const panel = await fillFromPanel(page, 'demo-admin')
  const code = (await panel.locator('code[aria-live]').textContent())?.trim() ?? ''
  expect(code).toMatch(/^\d{6}$/)
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()

  // Pre-enrolled, with no forced change: straight to the challenge. The server accepts one step of skew, so the code
  // read a moment ago still verifies if its step has just ended.
  await expect(page.getByRole('heading', { name: 'TOTP Verification' })).toBeVisible({ timeout: 20_000 })
  // Signed in as a demo account, the prompt itself shows the code too.
  await expect(page.getByRole('group', { name: 'Demo code' })).toBeVisible()
  await page.getByLabel('Code from the app').fill(code)
  await page.keyboard.press('Enter')

  await expect(page).toHaveURL(/\/admin\/users$/)
  await expect(page.getByRole('table').getByRole('link', { name: 'demo-user', exact: true })).toBeVisible()

  await page.getByRole('link', { name: target, exact: true }).click()
  await expect(page.getByRole('button', { name: 'Disable account' })).toBeVisible()
  // Eleven minutes pass for this session's factor: the next change needs a fresh code.
  const aged = await request.post(`${process.env.VITE_API_ORIGIN ?? ''}/e2e/factor-age?username=demo-admin`)
  expect(aged.status()).toBe(204)

  await page.getByRole('button', { name: 'Disable account' }).click()
  const dialog = page.getByRole('dialog', { name: 'TOTP Verification' })
  await expect(dialog).toBeVisible()
  // The sign-in's code can never verify again: while it is current the hint says so and offers no Fill in.
  const dialogHint = dialog.getByRole('group', { name: 'Demo code' })
  await dialogHint.getByRole('button', { name: 'Fill in demo code' }).click({ timeout: 40_000 })
  await dialog.getByRole('button', { name: 'Verify' }).click()

  await expect(dialog).toBeHidden()
  await expect(page.getByRole('status').filter({ hasText: 'Account disabled.' })).toHaveText(
    'Account disabled. The user has been signed out.',
  )

  // Still inside the fresh factor's window: the re-enable needs no code.
  await page.getByRole('button', { name: 'Enable account' }).click()
  await expect(page.getByRole('status').filter({ hasText: 'Account enabled.' })).toBeVisible()
})
