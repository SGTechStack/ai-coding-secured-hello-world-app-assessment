import { expect, test } from '@playwright/test'
import { signIn, usernameFor } from './fixtures.ts'

test('T-E2E-002: signs in, sees the greeting and signs out, against the real backend', async ({
  page,
  context,
}, testInfo) => {
  const username = usernameFor(testInfo, 'hello')
  const apiOrigin = process.env.VITE_API_ORIGIN!

  await signIn(page, username)
  await expect(page).toHaveURL(/\/hello$/)

  // T-E2E-002: the API's SameSite=Strict session cookie is sent on the SPA's cross-origin, same-site API calls.
  const [session] = (await context.cookies(apiOrigin)).filter((cookie) => cookie.name === 'SESSION')
  expect(session).toMatchObject({ httpOnly: true, sameSite: 'Strict' })

  const logout = page.waitForResponse((response) => response.url() === `${apiOrigin}/api/logout`)
  await page.getByRole('button', { name: 'Sign out' }).click()
  expect((await logout).status()).toBe(204)
  await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible()
  await expect(page).toHaveURL(/\/sign-in$/)

  // The session is over on the server too: the entry route lands on sign-in again.
  await page.goto('/')
  await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible()
})

test('T-E2E-003 T-E2E-004: a wrong password is refused through real-origin CORS with the uniform message', async ({
  page,
}, testInfo) => {
  const apiOrigin = process.env.VITE_API_ORIGIN!
  await page.goto('/')
  await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible()
  await page.getByLabel('Username').fill(usernameFor(testInfo, 'hello'))
  await page.getByLabel('Password').fill('not-the-password-at-all')

  const login = page.waitForResponse((response) => response.url() === `${apiOrigin}/api/login`)
  await page.getByRole('button', { name: 'Sign in' }).click()

  // The browser let the SPA read the envelope, so CORS allowed this origin with credentials.
  const response = await login
  expect(response.status()).toBe(401)
  expect(response.headers()['access-control-allow-origin']).toBe(new URL(page.url()).origin)
  await expect(page.getByRole('alert')).toHaveText('The username or password is not correct.')
})
