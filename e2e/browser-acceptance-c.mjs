// Focused check: does a password reset invalidate an existing live session?
// 1) ctx2 logs in as carol (verified), 2) requests a reset for carol,
// 3) waits for token file e2e/.reset-token, 4) confirms via UI, 5) re-probes session.
import { chromium } from 'playwright';
import fs from 'node:fs';

const BASE = 'http://localhost:3000';
const API = 'http://localhost:8080';
const TOKEN_FILE = new URL('./.reset-token', import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1');
try { fs.unlinkSync(TOKEN_FILE); } catch {}

const browser = await chromium.launch({
  executablePath: 'C:\\Users\\admin\\AppData\\Local\\ms-playwright\\chromium_headless_shell-1243\\chrome-headless-shell-win64\\chrome-headless-shell.exe',
});
const ctx = await browser.newContext();
const page = await ctx.newPage();

await page.goto(`${BASE}/login`);
await page.getByLabel('Username').fill('carol_chrome');
await page.getByLabel('Password').fill('chrome-pass-12345');
await page.getByRole('button', { name: 'Sign in' }).click();
await page.getByText('Hello, carol_chrome').waitFor({ timeout: 15000 });
const hello1 = await ctx.request.get(`${API}/api/hello`);
console.log(`live session check -> ${hello1.status()} (expect 200)`);

// request a fresh reset token for carol (public endpoint, needs CSRF)
const csrf = await ctx.request.get(`${API}/api/auth/csrf`);
const token = (await csrf.json()).token;
const req = await ctx.request.post(`${API}/api/auth/password-reset/request`, {
  data: { email: 'carol_chrome@example.com' },
  headers: { 'X-XSRF-TOKEN': token },
});
console.log(`reset request -> ${req.status()}`);
console.log('WAITING_FOR_TOKEN_FILE');

for (let i = 0; i < 60; i++) {
  if (fs.existsSync(TOKEN_FILE)) break;
  await new Promise(r => setTimeout(r, 2000));
}
const resetToken = fs.readFileSync(TOKEN_FILE, 'utf8').trim();

// keep the session probe independent: confirm reset in a second context
const ctxB = await browser.newContext();
const pageB = await ctxB.newPage();
await pageB.goto(`${BASE}/reset-password?token=${resetToken}`);
await pageB.getByLabel('New password').fill('carol-final-pass-7');
await pageB.locator('form').getByRole('button').click();
await pageB.waitForTimeout(1500);
console.log('confirm submitted');

const hello2 = await ctx.request.get(`${API}/api/hello`);
console.log(`RESULT session-after-reset -> ${hello2.status()} (expect 401)`);
await browser.close();
