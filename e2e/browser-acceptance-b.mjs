// Browser acceptance driver — phase B (reset confirm, session invalidation, login negatives, throttle).
// Usage: node browser-acceptance-b.mjs <reset-token>
import { chromium } from 'playwright';
import fs from 'node:fs';

const TOKEN = process.argv[2];
const BASE = 'http://localhost:3000';
const API = 'http://localhost:8080';
const SHOTS = new URL('../artifacts/browser-test/screenshots-cli/', import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1');
fs.mkdirSync(SHOTS, { recursive: true });

const results = [];
const rec = (id, status, note = '') => { results.push({ id, status, note }); console.log(`${status} | ${id} | ${note}`); };

const browser = await chromium.launch({
  executablePath: 'C:\\Users\\admin\\AppData\\Local\\ms-playwright\\chromium_headless_shell-1243\\chrome-headless-shell-win64\\chrome-headless-shell.exe',
});
const ctx = await browser.newContext();
const page = await ctx.newPage();
const shot = (n) => page.screenshot({ path: `${SHOTS}/${n}.png`, fullPage: true });

async function loginOn(p, u, pw) {
  await p.goto(`${BASE}/login`);
  await p.getByLabel('Username').fill(u);
  await p.getByLabel('Password').fill(pw);
  await p.getByRole('button', { name: 'Sign in' }).click();
}
const alertText = async (p) => (await p.getByRole('alert').first().textContent().catch(() => '')) || '';

// --- Pre-reset: a second live session for carol (to prove reset kills sessions) ---
const ctx2 = await browser.newContext();
const page2 = await ctx2.newPage();
await loginOn(page2, 'carol_chrome', 'chrome-pass-12345');
const preHello = await ctx2.request.get(`${API}/api/hello`);
rec('S7-pre-reset-session', preHello.status() === 200 ? 'PASS' : 'FAIL', `carol session /api/hello -> ${preHello.status()}`);

// --- Story 7: reset confirm via the emailed link ---
await page.goto(`${BASE}/reset-password?token=${TOKEN}`);
await page.getByLabel('New password').fill('carol-new-pass-99');
await page.locator('form').getByRole('button').click();
await page.waitForTimeout(1500);
const confirmTxt = await page.locator('main').innerText();
const confirmed = /updated|success|sign in/i.test(confirmTxt);
rec('S7-reset-confirm', confirmed ? 'PASS' : 'FAIL', confirmTxt.replace(/\n+/g, ' ').slice(0, 140));
await shot('b01-reset-confirm');

// reset must invalidate carol's existing session (ctx2)
const postHello = await ctx2.request.get(`${API}/api/hello`);
rec('S7-session-purge', postHello.status() === 401 ? 'PASS' : 'FAIL', `old session /api/hello after reset -> ${postHello.status()}`);
await ctx2.close();

// --- single-use: replay the same token ---
await page.goto(`${BASE}/reset-password?token=${TOKEN}`);
await page.getByLabel('New password').fill('carol-other-pass-1');
await page.locator('form').getByRole('button').click();
await page.waitForTimeout(1500);
const replayTxt = await alertText(page);
rec('S7-token-single-use', /invalid|expired/i.test(replayTxt) ? 'PASS' : 'FAIL', `replay -> "${replayTxt.trim()}"`);
await shot('b02-token-replay');

// --- missing token page ---
await page.goto(`${BASE}/reset-password`);
await page.waitForTimeout(800);
const noTok = await alertText(page);
rec('S7-missing-token', /missing/i.test(noTok) ? 'PASS' : 'FAIL', `"${noTok.trim()}"`);

// --- Story 2: old password rejected, new accepted ---
await loginOn(page, 'carol_chrome', 'chrome-pass-12345');
const oldMsg = await alertText(page);
rec('S2-old-pw-rejected', /invalid username or password/i.test(oldMsg) ? 'PASS' : 'FAIL', `old pw -> "${oldMsg.trim()}"`);

await loginOn(page, 'carol_chrome', 'carol-new-pass-99');
const newOk = await page.getByText('Hello, carol_chrome').waitFor({ timeout: 10000 }).then(() => true).catch(() => false);
rec('S2-new-pw-accepted', newOk ? 'PASS' : 'FAIL', 'login with reset password lands on greeting');
await shot('b03-carol-new-login');

// sign out for the failure/throttle battery
await page.goto(BASE + '/');
await page.getByRole('button', { name: 'Sign out' }).click();
await page.waitForURL('**/login');

// --- Story 2 negative: wrong password and unknown user -> identical generic error ---
await loginOn(page, 'carol_chrome', 'wrong-password-x');
const wrongMsg = (await alertText(page)).trim();
await loginOn(page, 'nosuchuser', 'whatever-pass-1');
const unknownMsg = (await alertText(page)).trim();
const identical = wrongMsg === unknownMsg && /invalid username or password/i.test(wrongMsg);
rec('S2-generic-failure', identical ? 'PASS' : 'FAIL', `wrong="${wrongMsg}" unknown="${unknownMsg}"`);

// --- Story 3: IP throttle — keep failing until 429, then confirm correct creds still throttled ---
let throttled = false;
for (let i = 0; i < 8 && !throttled; i++) {
  await loginOn(page, 'carol_chrome', `throttle-probe-${i}`);
  throttled = /too many/i.test(await alertText(page));
}
let correctThrottled = false;
if (throttled) {
  await loginOn(page, 'carol_chrome', 'carol-new-pass-99');
  correctThrottled = /too many/i.test(await alertText(page));
}
rec('S3-ip-throttle', throttled && correctThrottled ? 'PASS' : 'FAIL',
  `429-wall=${throttled}, correct-creds-blocked=${correctThrottled}`);
await shot('b04-throttled');

fs.writeFileSync(`${SHOTS}/results-b.json`, JSON.stringify(results, null, 2));
await browser.close();
console.log('PHASE B DONE');
