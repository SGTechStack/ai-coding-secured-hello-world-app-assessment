import { expect, test } from '@playwright/test'
import { signIn } from './fixtures.ts'

const PASSWORD = 'velvet harbour quietly hums'

test('registers, activates from the emailed link and signs in, against the real backend', async ({
  page,
  request,
}, testInfo) => {
  const apiOrigin = process.env.VITE_API_ORIGIN!
  const spaOrigin = String(testInfo.project.use.baseURL)
  // A fresh name per attempt, so a retry never meets its own earlier registration.
  const username = `e2e-${testInfo.project.name}-${Date.now().toString(36)}`
  const email = `${username}@example.test`

  await page.goto('/sign-in')
  await page.getByRole('link', { name: 'Register' }).click()
  await page.getByLabel('Username').fill(username)
  await page.getByLabel('Email address').fill(email)
  await page.getByRole('button', { name: 'Register' }).click()
  await expect(page.getByRole('status')).toHaveText(/an activation link is on its way/, { timeout: 20_000 })

  // The e2e backend's mailbox (test sources only) holds the link the stubbed email service would have logged in dev.
  const mail = await request.get(`${apiOrigin}/e2e/mailbox?to=${encodeURIComponent(email)}`)
  expect(mail.status()).toBe(200)
  const { type, link } = (await mail.json()) as { type: string; link: string }
  expect(type).toBe('ACTIVATION')
  // The link is built on the configured SPA origin, never on anything the request said (REJ-022).
  expect(link.startsWith(`${spaOrigin}/activate#token=`)).toBe(true)

  // A pending registration cannot sign in: the uniform message, as for any wrong credentials.
  await page.goto('/sign-in')
  await page.getByLabel('Username').fill(username)
  await page.getByLabel('Password').fill(PASSWORD)
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page.getByRole('alert')).toHaveText('The username or password is not correct.', { timeout: 20_000 })

  await page.goto(link)
  await expect(page.getByRole('heading', { name: 'Activate your account' })).toBeVisible()
  await page.getByLabel('Choose a password').fill(PASSWORD)
  await page.getByRole('button', { name: 'Activate' }).click()
  // Activation sets the password at the production BCrypt cost, so allow more than the default 5 s.
  await expect(page.getByRole('status')).toHaveText('Your password is set. You can now sign in.', {
    timeout: 20_000,
  })

  await signIn(page, username, PASSWORD)
})
