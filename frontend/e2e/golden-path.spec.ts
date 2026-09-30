import { expect, type Page, test } from '@playwright/test'
import { PASSWORD, totp, usernameFor } from './fixtures.ts'

/** RFC 4648 Base32, as the manual-entry key is shown (spaces are cosmetic). */
function base32Decode(text: string): Buffer {
  const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567'
  let bits = ''
  for (const char of text.replace(/\s/g, '')) {
    bits += alphabet.indexOf(char).toString(2).padStart(5, '0')
  }
  const bytes = bits.match(/.{8}/g) ?? []
  return Buffer.from(bytes.map((byte) => parseInt(byte, 2)))
}

async function signIn(page: Page, username: string) {
  await page.goto('/')
  await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible()
  await page.getByLabel('Username').fill(username)
  await page.getByLabel('Password').fill(PASSWORD)
  await page.getByRole('button', { name: 'Sign in' }).click()
}

test('T-E2E-005 T-HDR-003: an administrator signs in, is sent to enrolment, then to the challenge, and reads the user list, with no CSP violation', async ({
  page,
}, testInfo) => {
  const username = usernameFor(testInfo, 'golden-path')
  // T-HDR-003: the production build under vite preview, served with the production policy (T-HDR-004), raises no
  // securitypolicyviolation anywhere on this path, the OTP field and the blob: QR code included.
  const violations: string[] = []
  await page.exposeFunction('reportCspViolation', (violation: string) => violations.push(violation))
  await page.addInitScript(() => {
    document.addEventListener('securitypolicyviolation', (event) => {
      const report = (window as unknown as { reportCspViolation: (v: string) => void }).reportCspViolation
      report(`${event.violatedDirective} ${event.blockedURI}`)
    })
  })

  // Gate order (ADR-023): an unenrolled administrator lands on enrolment. BCrypt at cost 12 on a cold backend is slow.
  await signIn(page, username)
  await expect(page.getByRole('heading', { name: 'Two-factor authentication' })).toBeVisible({ timeout: 20_000 })
  await page.getByRole('button', { name: 'Generate QR code' }).click()

  // The server's PNG, through a blob: URL the document CSP allows (ADR-025; ADR-060), and actually decoded.
  const qr = page.getByRole('img', { name: 'QR code for your authenticator app' })
  await expect(qr).toHaveAttribute('src', /^blob:/)
  await expect.poll(() => qr.evaluate((image: HTMLImageElement) => image.naturalWidth)).toBeGreaterThan(0)
  const secret = base32Decode((await page.locator('code').textContent()) ?? '')
  await page.getByLabel('Code from the app').fill(totp(secret))
  await page.getByRole('button', { name: 'Confirm' }).click()
  await expect(page.getByRole('status')).toHaveText(/Your authenticator app is set up/)
  await expect(qr).toHaveCount(0)

  // Enrolment granted the factor, so the user list opens.
  await page.getByRole('link', { name: 'Continue to the user list' }).click()
  await expect(page.getByRole('link', { name: username })).toBeVisible()

  // A new session holds the password only: the eager challenge, then the list (ADR-021).
  await page.getByRole('button', { name: 'Sign out' }).click()
  await signIn(page, username)
  await expect(page.getByRole('heading', { name: 'TOTP Verification' })).toBeVisible({ timeout: 20_000 })
  await expect(page.getByLabel('Code from the app')).toBeFocused()
  // Enrolment used this step's code, and a used step is a replay (R-MFA-017), so answer with the next one: the
  // server accepts one step of skew either way.
  await page.keyboard.type(totp(secret, Date.now() + 30_000))
  await page.keyboard.press('Enter')

  await expect(page).toHaveURL(/\/admin\/users$/)
  const table = page.getByRole('table')
  await expect(table.getByRole('link', { name: username })).toBeVisible()
  await expect(table.getByRole('row', { name: new RegExp(username) })).toContainText('ADMIN')
  await expect(page.locator('body')).not.toContainText('$2a$')
  expect(violations).toEqual([])
})
