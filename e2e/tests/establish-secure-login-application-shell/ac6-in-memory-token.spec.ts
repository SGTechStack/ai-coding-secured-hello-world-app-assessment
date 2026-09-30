import { allure } from 'allure-playwright';
import { expect, test, enterCredentials, greeting, SEED_USER } from '../../fixtures/session';
import { acceptance, check } from '../../fixtures/acceptance';

const AC_DESCRIPTION = 'AC6: The frontend stores the CSRF token in memory for use by the first real mutating request in Ticket 02.';

test(AC_DESCRIPTION, async ({ page }) => {
  await acceptance('US1: Establish the secure login application shell', AC_DESCRIPTION, 'Registered user on /login (local seed johndoe)');
  await allure.description(AC_DESCRIPTION);
  await allure.attachment('Test plan', [
    'Data:',
    '- Local-profile seed account johndoe / Password123! (e2e/README.md, permitted test oracle). Discovery: submit calls GET /csrf, then POST /api/auth/login whose X-CSRF-TOKEN equals the /csrf body token; web storage stays empty.',
    'Isolation:',
    '- Isolated E2E stack: harness lifecycle (e2e/scripts/lifecycle.mjs) starts the backend with the local profile on a fresh in-memory H2 database; only this browser context signs in.',
    'Steps:',
    '1. Open /login as the registered user.',
    '2. Enter username and password and select Log in.',
    '3. Wait for the protected application to open.',
    '4. Inspect the browser storage for the CSRF token.',
    'Visible outcomes:',
    '- the first login POST carries the /csrf token in the X-CSRF-TOKEN header',
    '- the CSRF token is absent from localStorage, sessionStorage and document.cookie',
    'Acceptance mapping:',
    '- AC6 | Story clause: for use by the first real mutating request | Actor: Registered user on /login | Visible outcome: the first login POST carries the /csrf token in the X-CSRF-TOKEN header',
    '- AC6 | Story clause: The frontend stores the CSRF token in memory | Actor: Registered user on /login | Visible outcome: the CSRF token is absent from localStorage, sessionStorage and document.cookie',
    'Assumptions:',
    '- The in-memory variable is private to the SPA; memory storage is proved by the header use plus absence from every script-readable persistence store.',
  ].join('\n'), 'text/plain');

  await enterCredentials(page, SEED_USER.username, SEED_USER.password);
  const csrfResponse = page.waitForResponse((r) => new URL(r.url()).pathname === '/csrf');
  const loginRequest = page.waitForRequest((r) => new URL(r.url()).pathname === '/api/auth/login' && r.method() === 'POST');
  await page.getByRole('button', { name: 'Log in', exact: true }).click();
  const csrf = await (await csrfResponse).json() as { token: string; headerName: string };
  const sent = await (await loginRequest).headerValue('X-CSRF-TOKEN');
  const protectedHeading = greeting(page, 'johndoe');
  await expect(protectedHeading).toBeVisible();
  await protectedHeading.scrollIntoViewIfNeeded();
  await check('Assertion: [AC6] actor: Registered user on /login | the first login POST carries the /csrf token in the X-CSRF-TOKEN header',
    'X-CSRF-TOKEN on the first login POST equals the /csrf body token, and the protected application opens',
    `header equals token=${sent === csrf.token} (length ${sent?.length ?? 0}); heading "${await protectedHeading.textContent()}"`, async () => {
      expect(sent).toBe(csrf.token);
      await expect(protectedHeading).toBeVisible();
    });

  const stores = await page.evaluate(() => ({
    localStorage: JSON.stringify(Object.fromEntries(Object.entries(localStorage))),
    sessionStorage: JSON.stringify(Object.fromEntries(Object.entries(sessionStorage))),
    documentCookie: document.cookie,
  }));
  await check('Assertion: [AC6] actor: Registered user on /login | the CSRF token is absent from localStorage, sessionStorage and document.cookie',
    'token found in none of the stores', `localStorage ${stores.localStorage}; sessionStorage ${stores.sessionStorage}; document.cookie "${stores.documentCookie}"`, async () => {
      for (const store of Object.values(stores)) expect(store).not.toContain(csrf.token);
    });
});
