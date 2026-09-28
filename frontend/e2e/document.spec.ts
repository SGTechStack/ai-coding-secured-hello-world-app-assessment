import path from 'node:path'
import { expect, test } from '@playwright/test'
import { loadEnv } from 'vite'

// The policy exactly as `vite build` resolves it from .env.production.
const productionCsp = () => loadEnv('production', path.resolve(import.meta.dirname, '..'), 'VITE_').VITE_CSP

test('T-HDR-004: vite preview sends the production CSP as a header with frame-ancestors and as a meta tag without it', async ({
  page,
}) => {
  const response = await page.goto('/')
  expect(response?.status()).toBe(200)

  const policy = productionCsp()
  expect(response?.headers()['content-security-policy']).toBe(`${policy}; frame-ancestors 'none'`)
  expect(response?.headers()['referrer-policy']).toBe('no-referrer')
  await expect(page.locator('meta[http-equiv="Content-Security-Policy"]')).toHaveAttribute('content', policy)
  await expect(page.getByRole('heading', { name: 'Secured Hello World' })).toBeVisible()
})

test('T-E2E-001: no service worker is registered', async ({ page }) => {
  // Load-only for now; the signed-in half of this row arrives with sign-in (ticket 10).
  await page.goto('/')
  await expect(page.getByRole('heading', { name: 'Secured Hello World' })).toBeVisible()
  expect(await page.evaluate(async () => (await navigator.serviceWorker.getRegistrations()).length)).toBe(0)
})
