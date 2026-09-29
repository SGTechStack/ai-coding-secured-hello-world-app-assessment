import { createHmac } from 'node:crypto'
import { expect, type Page, test } from '@playwright/test'
import { PASSWORD, usernameFor } from './fixtures.ts'

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

/** The RFC 6238 code at `at`: HMAC-SHA1, 30-second step, 6 digits. */
function totp(secret: Buffer, at = Date.now()): string {
  const counter = Buffer.alloc(8)
  counter.writeBigUInt64BE(BigInt(Math.floor(at / 1000 / 30)))
  const hash = createHmac('sha1', secret).update(counter).digest()
  const offset = hash[hash.length - 1] & 0x0f
  return String((hash.readUInt32BE(offset) & 0x7fffffff) % 1_000_000).padStart(6, '0')
}

async function signIn(page: Page, username: string) {
  await page.goto('/')
  await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible()
  await page.getByLabel('Username').fill(username)
  await page.getByLabel('Password').fill(PASSWORD)
  await page.getByRole('button', { name: 'Sign in' }).click()
}

test('T-E2E-005: an administrator signs in, is sent to enrolment, then to the challenge, and reads the user list', async ({
  page,
}, testInfo) => {
  const username = usernameFor(testInfo, 'golden-path')

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
})
