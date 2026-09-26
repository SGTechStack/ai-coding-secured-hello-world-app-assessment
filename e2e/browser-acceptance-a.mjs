// Browser acceptance driver — phase A (registration, login, logout, admin, reset-request).
// Run: node browser-acceptance-a.mjs   (from e2e/; stack must be live on :3000/:8080)
import { chromium } from 'playwright';
import fs from 'node:fs';

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
page.on('dialog', d => d.accept());
const shot = (n) => page.screenshot({ path: `${SHOTS}/${n}.png`, fullPage: true });

async function login(u, p) {
  await page.goto(`${BASE}/login`);
  await page.getByLabel('Username').fill(u);
  await page.getByLabel('Password').fill(p);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await page.waitForURL(`${BASE}/`, { timeout: 10000 });
  await page.getByText('Hello,').waitFor({ timeout: 10000 });
}
async function logout() {
  await page.goto(BASE + '/');
  await page.getByRole('button', { name: 'Sign out' }).click();
  await page.waitForURL('**/login');
}

// --- Route guards: anonymous / -> /login ---
await page.goto(BASE + '/');
await page.waitForURL('**/login', { timeout: 8000 }).catch(() => {});
rec('guard-anon', page.url().endsWith('/login') ? 'PASS' : 'FAIL', `landed on ${page.url()}`);

// --- Story 1: register happy path ---
await page.goto(`${BASE}/register`);
await page.getByLabel('Username').fill('dave_chrome');
await page.getByLabel('Email').fill('dave_chrome@example.com');
await page.getByLabel('Password').fill('dave-pass-12345');
await page.getByRole('button', { name: 'Create account' }).click();
try {
  await page.getByText('Hello, dave_chrome').waitFor({ timeout: 10000 });
  rec('S1-register', 'PASS', 'greeting shown, signed in as USER');
  await shot('a01-hello-dave');
} catch { rec('S1-register', 'FAIL', 'no greeting after register'); }

// --- Seed carol + throwaway victim via API (public endpoint, needs CSRF) ---
const seed = await ctx.request.get(`${API}/api/auth/csrf`);
const seedToken = (await seed.json()).token;
for (const [u, e] of [['carol_chrome', 'carol_chrome@example.com'], ['victim_user', 'victim@example.com']]) {
  const r = await ctx.request.post(`${API}/api/auth/register`, {
    data: { username: u, email: e, password: 'chrome-pass-12345' },
    headers: { 'X-XSRF-TOKEN': seedToken },
  });
  rec(`seed-${u}`, r.status() === 201 || r.status() === 409 ? 'PASS' : 'FAIL', `register ${u} -> ${r.status()}`);
}

// --- Story 4: logout + server-side invalidation + cookie replay ---
const preLogout = (await ctx.cookies(API)).find(c => c.name === 'SESSION');
await logout();
const anon = await ctx.request.get(`${API}/api/hello`);
rec('S5-anon-hello-401', anon.status() === 401 ? 'PASS' : 'FAIL', `GET /api/hello anon -> ${anon.status()}`);
if (preLogout) {
  const replay = await ctx.request.get(`${API}/api/hello`, {
    headers: { Cookie: `SESSION=${preLogout.value}` },
  });
  rec('S4-cookie-replay', replay.status() === 401 ? 'PASS' : 'FAIL', `replayed SESSION -> ${replay.status()}`);
} else rec('S4-cookie-replay', 'SKIP', 'no SESSION cookie captured');

// --- Story 1 negatives: duplicate username, duplicate email, weak password ---
await page.goto(`${BASE}/register`);
await page.getByLabel('Username').fill('carol_chrome');
await page.getByLabel('Email').fill('unique1@example.com');
await page.getByLabel('Password').fill('valid-pass-1234');
await page.getByRole('button', { name: 'Create account' }).click();
const dupU = await page.getByRole('alert').textContent().catch(() => '');
rec('S1-dup-username', /already taken/i.test(dupU || '') ? 'PASS' : 'FAIL', (dupU || '').trim());

await page.getByLabel('Username').fill('dave_other');
await page.getByLabel('Email').fill('carol_chrome@example.com');
await page.getByLabel('Password').fill('valid-pass-1234');
await page.getByRole('button', { name: 'Create account' }).click();
const dupE = await page.getByRole('alert').textContent().catch(() => '');
rec('S1-dup-email', /already registered/i.test(dupE || '') ? 'PASS' : 'FAIL', (dupE || '').trim());

await page.getByLabel('Username').fill('weakling');
await page.getByLabel('Email').fill('weakling@example.com');
await page.getByLabel('Password').fill('short');
await page.getByRole('button', { name: 'Create account' }).click();
await page.waitForTimeout(500);
const vm = await page.$eval('input[type="password"]', el => el.validationMessage).catch(() => '');
rec('S1-weak-pw', page.url().includes('/register') && vm ? 'PASS' : 'FAIL', `client blocked: ${vm}`);

// --- CSRF: register without token -> 403 ---
const noCsrf = await ctx.request.post(`${API}/api/auth/register`, {
  data: { username: 'nocsrf', email: 'nocsrf@example.com', password: 'nocsrf-pass-12' },
});
rec('CSRF-no-token', noCsrf.status() === 403 ? 'PASS' : 'FAIL', `POST register w/o token -> ${noCsrf.status()}`);

// --- Login dave; authed-user bounces, session persistence, USER->admin 403 ---
await login('dave_chrome', 'dave-pass-12345');
await page.getByText('Hello, dave_chrome').waitFor({ timeout: 10000 });

await page.goto(`${BASE}/login`);
await page.waitForTimeout(1500);
rec('guard-authed-login', !page.url().endsWith('/login') ? 'PASS' : 'FAIL', `authed /login -> ${page.url()}`);

await page.goto(`${BASE}/admin`);
await page.waitForTimeout(1500);
rec('guard-user-admin', page.url().endsWith('/') ? 'PASS' : 'FAIL', `USER /admin -> ${page.url()}`);

await page.reload();
const persisted = await page.getByText('Hello, dave_chrome').waitFor({ timeout: 8000 }).then(() => true).catch(() => false);
rec('S5-persistence', persisted ? 'PASS' : 'FAIL', 'reload keeps session');

const adm403 = await ctx.request.get(`${API}/api/admin/users`);
rec('S8-user-403', adm403.status() === 403 ? 'PASS' : 'FAIL', `USER GET /api/admin/users -> ${adm403.status()}`);

await logout();

// --- Stories 8-11: admin panel ---
await login('admin', 'admin-local-dev-password');
await page.goto(`${BASE}/admin`);
await page.getByText('carol_chrome', { exact: true }).waitFor({ timeout: 10000 });
await shot('a02-admin-panel');

const adminRow = page.locator('tr', { hasText: '(you)' });
const adminBtns = adminRow.getByRole('button');
const nSelf = await adminBtns.count();
let selfDisabled = nSelf > 0;
for (let i = 0; i < nSelf; i++) selfDisabled &&= await adminBtns.nth(i).isDisabled();
rec('S9-11-self-guard', selfDisabled ? 'PASS' : 'FAIL', `${nSelf} self-row buttons disabled`);

const carolRow = page.locator('tr', { has: page.getByText('carol_chrome', { exact: true }) });
await carolRow.getByRole('button', { name: 'Disable' }).click();
const disabledNow = await carolRow.getByText('Disabled').waitFor({ timeout: 8000 }).then(() => true).catch(() => false);
await shot('a03-carol-disabled');

// disabled carol cannot log in (generic error) — failure #1 toward IP throttle
const c2 = await browser.newContext(); const p2 = await c2.newPage();
await p2.goto(`${BASE}/login`);
await p2.getByLabel('Username').fill('carol_chrome');
await p2.getByLabel('Password').fill('chrome-pass-12345');
await p2.getByRole('button', { name: 'Sign in' }).click();
const disMsg = await p2.getByRole('alert').textContent().catch(() => '');
const disOk = /invalid username or password/i.test(disMsg || '');
await c2.close();
await carolRow.getByRole('button', { name: 'Enable' }).click();
await carolRow.getByText('Enabled').waitFor({ timeout: 8000 }).catch(() => {});
const c3 = await browser.newContext(); const p3 = await c3.newPage();
await p3.goto(`${BASE}/login`);
await p3.getByLabel('Username').fill('carol_chrome');
await p3.getByLabel('Password').fill('chrome-pass-12345');
await p3.getByRole('button', { name: 'Sign in' }).click();
const reenabled = await p3.getByText('Hello, carol_chrome').waitFor({ timeout: 10000 }).then(() => true).catch(() => false);
await c3.close();
rec('S9-disable-enable', disabledNow && disOk && reenabled ? 'PASS' : 'FAIL',
  `row=${disabledNow} disabled-login-generic=${disOk} reenabled-login=${reenabled}`);

// role change USER -> ADMIN -> back
await carolRow.getByRole('button', { name: 'Make admin' }).click();
const becameAdmin = await carolRow.getByText('ADMIN', { exact: true }).waitFor({ timeout: 8000 }).then(() => true).catch(() => false);
await carolRow.getByRole('button', { name: 'Revoke admin' }).click();
const backUser = await carolRow.getByText('USER', { exact: true }).waitFor({ timeout: 8000 }).then(() => true).catch(() => false);
rec('S10-role-change', becameAdmin && backUser ? 'PASS' : 'FAIL', `->ADMIN ${becameAdmin}, back to USER ${backUser}`);

// delete victim
const victimRow = page.locator('tr', { hasText: 'victim_user' });
await victimRow.getByRole('button', { name: 'Delete' }).click();
await page.waitForTimeout(800);
const gone = !(await page.locator('tr', { hasText: 'victim_user' }).count());
rec('S11-delete', gone ? 'PASS' : 'FAIL', 'victim_user row removed after confirm');
await shot('a04-admin-after');

await logout();

// --- Story 6: reset request — generic response for unknown vs known email ---
async function resetRequest(email) {
  await page.goto(`${BASE}/forgot-password`);
  await page.getByLabel('Email').fill(email);
  await page.locator('form').getByRole('button').click();
  await page.waitForTimeout(1500);
  return (await page.locator('main').innerText()).trim();
}
const msgUnknown = await resetRequest('nobody@example.com');
const msgKnown = await resetRequest('carol_chrome@example.com');
const sameMsg = msgUnknown === msgKnown && /if|sent|email/i.test(msgUnknown);
rec('S6-generic-reset', sameMsg ? 'PASS' : 'FAIL', `identical=${msgUnknown === msgKnown}: "${msgUnknown.replace(/\n+/g, ' ').slice(0, 120)}"`);
await shot('a05-reset-request');

fs.writeFileSync(`${SHOTS}/results-a.json`, JSON.stringify(results, null, 2));
await browser.close();
console.log('PHASE A DONE');
