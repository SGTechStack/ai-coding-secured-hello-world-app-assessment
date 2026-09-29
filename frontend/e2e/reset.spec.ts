import { expect, test } from '@playwright/test'
import { signIn, usernameFor } from './fixtures.ts'

test('requests a reset, sets a new password from the emailed link and signs in, against the real backend', async ({
  page,
  request,
}, testInfo) => {
  const apiOrigin = process.env.VITE_API_ORIGIN!
  const spaOrigin = String(testInfo.project.use.baseURL)
  const username = usernameFor(testInfo, 'reset')
  const email = `${username}@example.test`
  // A fresh password per attempt: a retry must not reuse the one its earlier attempt set (password history).
  const newPassword = `copper lantern ${Date.now().toString(36)} drifts west`

  await page.goto('/sign-in')
  await page.getByRole('link', { name: 'Forgot password?' }).click()
  await page.getByLabel('Email address').fill(email)
  await page.getByRole('button', { name: 'Send reset link' }).click()
  await expect(page.getByRole('status')).toHaveText(/a reset link is on its way/, { timeout: 20_000 })

  // The e2e backend's mailbox (test sources only) holds the link the stubbed email service would have logged in dev.
  const mail = await request.get(`${apiOrigin}/e2e/mailbox?to=${encodeURIComponent(email)}`)
  expect(mail.status()).toBe(200)
  const { type, link } = (await mail.json()) as { type: string; link: string }
  expect(type).toBe('PASSWORD_RESET')
  // The link is built on the configured SPA origin, never on anything the request said (REJ-022).
  expect(link.startsWith(`${spaOrigin}/reset#token=`)).toBe(true)

  await page.goto(link)
  await expect(page.getByRole('heading', { name: 'Reset your password' })).toBeVisible()
  await page.getByLabel('Choose a password').fill(newPassword)
  await page.getByRole('button', { name: 'Reset password' }).click()
  // Redemption sets the password at the production BCrypt cost, so allow more than the default 5 s.
  await expect(page.getByRole('status')).toHaveText('Your password is set. You can now sign in.', {
    timeout: 20_000,
  })

  // The link works once. Leave the page first: the same URL again would be a same-document navigation.
  await page.goto('/sign-in')
  await page.goto(link)
  await page.getByLabel('Choose a password').fill(`${newPassword} again`)
  await page.getByRole('button', { name: 'Reset password' }).click()
  await expect(page.getByRole('alert').filter({ hasText: 'This reset link is not valid' })).toBeVisible({
    timeout: 20_000,
  })

  await signIn(page, username, newPassword)
})
