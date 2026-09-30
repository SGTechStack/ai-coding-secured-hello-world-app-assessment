import { type Page, expect, test } from '@playwright/test'
import { PASSWORD, signIn, usernameFor } from './fixtures.ts'

/** Submits the sign-in form and returns the login response's status. */
async function attempt(page: Page, username: string, password: string): Promise<number> {
  const apiOrigin = process.env.VITE_API_ORIGIN!
  await page.getByLabel('Username').fill(username)
  await page.getByLabel('Password').fill(password)
  const login = page.waitForResponse((response) => response.url() === `${apiOrigin}/api/login`)
  await page.getByRole('button', { name: 'Sign in' }).click()
  return (await login).status()
}

test("T-E2E-006: an attacker's fresh browser locks the account, and the owner's trusted browser still signs in", async ({
  page,
  browser,
}, testInfo) => {
  const username = usernameFor(testInfo, 'device-lockout')
  const apiOrigin = process.env.VITE_API_ORIGIN!

  // The owner's correct password earns this browser a device cookie (ADR-075).
  await signIn(page, username)
  const [device] = (await page.context().cookies(apiOrigin)).filter((cookie) => cookie.name === 'DEVICE')
  expect(device).toMatchObject({ httpOnly: true, sameSite: 'Strict' })
  // The owner's session ends without a sign-out, as an idle timeout ends it; the device cookie stays.
  await page.context().clearCookies({ name: 'SESSION' })

  // The attacker, in a fresh browser with no device cookie, locks the account's untrusted lane.
  const attacker = await browser.newContext()
  try {
    const attackerPage = await attacker.newPage()
    await attackerPage.goto('/')
    await expect(attackerPage.getByRole('heading', { name: 'Sign in' })).toBeVisible()
    for (let i = 0; i < 5; i++) {
      expect(await attempt(attackerPage, username, 'not-the-password-at-all')).toBe(401)
    }
    // Locked: even the correct password is refused there, with the uniform message.
    expect(await attempt(attackerPage, username, PASSWORD)).toBe(401)
    await expect(attackerPage.getByRole('alert')).toHaveText('The username or password is not correct.')
  } finally {
    await attacker.close()
  }

  // The owner's browser, holding its device cookie, still signs in.
  await page.goto('/')
  await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible()
  await signIn(page, username)
  await expect(page).toHaveURL(/\/hello$/)
})

test("T-E2E-007: a signed-out browser keeps its device cookie and still signs in through the attacker's lock", async ({
  page,
  browser,
}, testInfo) => {
  const username = usernameFor(testInfo, 'device-signout')
  const apiOrigin = process.env.VITE_API_ORIGIN!

  await signIn(page, username)
  const logout = page.waitForResponse((response) => response.url() === `${apiOrigin}/api/logout`)
  await page.getByRole('button', { name: 'Sign out' }).click()
  expect((await logout).status()).toBe(204)
  await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible()
  // Sign-out expires the session cookie and leaves the device cookie (ADR-075).
  const names = (await page.context().cookies(apiOrigin)).map((cookie) => cookie.name)
  expect(names).not.toContain('SESSION')
  expect(names).toContain('DEVICE')

  const attacker = await browser.newContext()
  try {
    const attackerPage = await attacker.newPage()
    await attackerPage.goto('/')
    await expect(attackerPage.getByRole('heading', { name: 'Sign in' })).toBeVisible()
    for (let i = 0; i < 5; i++) {
      expect(await attempt(attackerPage, username, 'not-the-password-at-all')).toBe(401)
    }
    expect(await attempt(attackerPage, username, PASSWORD)).toBe(401)
  } finally {
    await attacker.close()
  }

  await signIn(page, username)
  await expect(page).toHaveURL(/\/hello$/)
})
