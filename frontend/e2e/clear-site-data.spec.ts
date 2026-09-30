import { expect, test } from '@playwright/test'

test("T-HDR-005: sign-out's Clear-Site-Data leaves out cookies, and reaches neither the SPA's cookies nor its storage", async ({
  page,
}) => {
  const apiOrigin = process.env.VITE_API_ORIGIN!
  await page.goto('/')
  await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible()
  // State the SPA origin owns: a cookie, which the header would reach across the registrable domain had it the
  // "cookies" directive, left out so sign-out keeps the device cookie (ADR-075), and storage, which it cannot reach
  // (W3C Clear Site Data; R-HDR-008).
  await page.evaluate(() => {
    document.cookie = 'spa-probe=1; path=/; SameSite=Strict'
    localStorage.setItem('spa-probe', '1')
  })
  expect(await page.evaluate(() => document.cookie)).toContain('spa-probe=1')

  const logout = page.waitForResponse((response) => response.url() === `${apiOrigin}/api/logout`)
  // The sign-out request as the SPA sends it: fetched with credentials, from the page, after a token bootstrap.
  const status = await page.evaluate(async (api) => {
    const csrf = await fetch(`${api}/api/csrf`, { credentials: 'include' })
    const { headerName, token } = (await csrf.json()) as { headerName: string; token: string }
    const signedOut = await fetch(`${api}/api/logout`, {
      method: 'POST',
      credentials: 'include',
      headers: { [headerName]: token },
    })
    return signedOut.status
  }, apiOrigin)
  const response = await logout

  expect(status).toBe(204)
  const header = (await response.allHeaders())['clear-site-data']
  expect(header).toContain('"cache"')
  expect(header).toContain('"storage"')
  expect(header).not.toContain('"cookies"')
  expect(response.fromServiceWorker()).toBe(false)
  expect(await page.evaluate(() => document.cookie)).toContain('spa-probe=1')
  expect(await page.evaluate(() => localStorage.getItem('spa-probe'))).toBe('1')
})
