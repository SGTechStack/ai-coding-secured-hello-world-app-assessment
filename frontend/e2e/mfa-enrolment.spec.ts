import { createHmac } from 'node:crypto'
import { expect, test } from '@playwright/test'
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

/** The current RFC 6238 code: HMAC-SHA1, 30-second step, 6 digits. */
function totp(secret: Buffer, now = Date.now()): string {
  const counter = Buffer.alloc(8)
  counter.writeBigUInt64BE(BigInt(Math.floor(now / 1000 / 30)))
  const hash = createHmac('sha1', secret).update(counter).digest()
  const offset = hash[hash.length - 1] & 0x0f
  return String((hash.readUInt32BE(offset) & 0x7fffffff) % 1_000_000).padStart(6, '0')
}

test('an administrator enrols a TOTP authenticator from the QR page by typing the shown key', async ({
  page,
}, testInfo) => {
  const username = usernameFor(testInfo, 'mfa-enrolment')
  await page.goto('/')
  await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible()
  await page.getByLabel('Username').fill(username)
  await page.getByLabel('Password').fill(PASSWORD)
  await page.getByRole('button', { name: 'Sign in' }).click()

  // BCrypt at cost 12 on a cold backend is slow.
  await page.getByRole('link', { name: 'Two-factor authentication' }).click({ timeout: 20_000 })
  await expect(page.getByRole('heading', { name: 'Two-factor authentication' })).toBeVisible()
  await page.getByRole('button', { name: 'Generate QR code' }).click()

  // The server's PNG, through a blob: URL the document CSP allows (ADR-025; ADR-060), and actually decoded.
  const qr = page.getByRole('img', { name: 'QR code for your authenticator app' })
  await expect(qr).toHaveAttribute('src', /^blob:/)
  await expect.poll(() => qr.evaluate((image: HTMLImageElement) => image.naturalWidth)).toBeGreaterThan(0)

  const key = (await page.locator('code').textContent()) ?? ''
  await page.getByLabel('Code from the app').fill(totp(base32Decode(key)))
  await page.getByRole('button', { name: 'Confirm' }).click()

  await expect(page.getByRole('status')).toHaveText(/Your authenticator app is set up/)
  await expect(qr).toHaveCount(0)
})
