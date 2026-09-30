import { allure } from 'allure-playwright';
import { expect, test, enterCredentials, greeting, cookieValue } from '../../fixtures/session';
import { acceptance, check } from '../../fixtures/acceptance';

const AC_DESCRIPTION = 'AC7: Successful authentication invalidates the pre-authentication session identifier and establishes a new authenticated session identifier.';

test(AC_DESCRIPTION, async ({ page, context, browser, baseURL, account }) => {
  await acceptance('US2: Authenticate a registered user and enter the protected application', AC_DESCRIPTION, 'Registered user');
  await allure.description(AC_DESCRIPTION);
  await allure.attachment('Test plan', [
    'Data:',
    '- Scenario-owned account (fixture registers it through POST /api/auth/register from a separate API context). Discovery: the login request carries the anonymous session cookie id=<pre-login>; the login response sets id=<new>; a fresh browser holding only the pre-login id opens / and ends at /login, its Session check (GET /api/profile) refused with 401, with no protected content; the new id is granted GET /api/profile (200).',
    'Isolation:',
    '- Isolated E2E stack: harness lifecycle (e2e/scripts/lifecycle.mjs) starts the backend with the local profile on a fresh in-memory H2 database; a second fresh browser context replays only the pre-login identifier.',
    'Steps:',
    '1. Open /login, enter the scenario-owned username and password and select Log in.',
    '2. Note the session identifier sent with the login and the one held after sign-in.',
    '3. In a separate fresh browser holding only the pre-sign-in identifier, open /.',
    '4. Back in the signed-in browser, confirm the protected application is shown with the new identifier, and that the server grants that browser GET /api/profile.',
    'Visible outcomes:',
    '- the pre-sign-in session identifier is replaced and no longer opens the protected application',
    '- the new session identifier is the signed-in session that shows the protected application',
    'Acceptance mapping:',
    '- AC7 | Story clause: invalidates the pre-authentication session identifier | Actor: Registered user | Visible outcome: the pre-sign-in session identifier is replaced and no longer opens the protected application',
    '- AC7 | Story clause: establishes a new authenticated session identifier | Actor: Registered user | Visible outcome: the new session identifier is the signed-in session that shows the protected application',
    'Assumptions:',
    '- AC7 concerns the server session identifier. The new identifier is proved in the signed-in tab (rendered landing, jar holds only the new id) with the server granting GET /api/profile to it. Pages hold no data (ADR 0007), so the server answers every page address with the app; the Session is proved by the API.',
  ].join('\n'), 'text/plain');

  await enterCredentials(page, account.username, account.password);
  const loginRequest = page.waitForRequest((r) => new URL(r.url()).pathname === '/api/auth/login' && r.method() === 'POST');
  const loginResponse = page.waitForResponse((r) => new URL(r.url()).pathname === '/api/auth/login');
  await page.getByRole('button', { name: 'Log in', exact: true }).click();
  const preLoginId = cookieValue((await (await loginRequest).allHeaders()).cookie, 'id');
  await loginResponse;
  const landing = greeting(page, account.username);
  await expect(landing).toBeVisible();
  const jarIds = (await context.cookies()).filter((c) => c.name === 'id');
  const newIdCookie = jarIds[0];
  // The signed-in browser's new identifier is granted the Session check.
  const newGrant = await context.request.get('/api/profile');

  const replay = await browser.newContext({ baseURL, ignoreHTTPSErrors: true });
  await replay.addCookies([{ ...newIdCookie, value: preLoginId ?? '' }]);
  const replayPage = await replay.newPage();
  const replayCheck = replayPage.waitForResponse((r) => new URL(r.url()).pathname === '/api/profile');
  await replayPage.goto('/');
  const replayRefused = await replayCheck;
  await replayPage.waitForURL('**/login');
  const replayProtected = replayPage.getByRole('heading', { name: /^Hello, / });
  await replayPage.waitForLoadState('load');
  const replayHeadings = await replayPage.getByRole('heading').allTextContents();
  const replayUrl = new URL(replayPage.url()).pathname;
  await check('Assertion: [AC7] actor: Registered user | the pre-sign-in session identifier is replaced and no longer opens the protected application',
    'jar id after sign-in differs from the pre-sign-in id; a browser holding only the pre-sign-in id is refused / and shows no Greeting',
    `pre-sign-in id ${preLoginId}; jar id after sign-in ${jarIds.map((c) => c.value).join(',')}; replay of pre-sign-in id: GET /api/profile -> ${replayRefused.status()}, landed at ${replayUrl}, headings [${replayHeadings.join(', ')}]`, async () => {
      expect(preLoginId).toBeTruthy();
      expect(jarIds).toHaveLength(1);
      expect(newIdCookie.value).not.toBe(preLoginId);
      expect(replayRefused.status()).toBe(401);
      expect(replayUrl).toBe('/login');
      await expect(replayProtected).toHaveCount(0);
    });
  await replay.close();

  await landing.scrollIntoViewIfNeeded();
  await check('Assertion: [AC7] actor: Registered user | the new session identifier is the signed-in session that shows the protected application',
    'signed-in tab shows the Greeting at / holding only the new id, and the server grants GET /api/profile to that id',
    `URL path ${new URL(page.url()).pathname}; heading "${await landing.textContent()}"; jar id ${newIdCookie.value}; GET /api/profile with the new id -> ${newGrant.status()}`, async () => {
      await expect(page).toHaveURL(/\/home$/);
      await expect(landing).toBeVisible();
      expect((await context.cookies()).filter((c) => c.name === 'id').map((c) => c.value)).toEqual([newIdCookie.value]);
      expect(newGrant.status()).toBe(200);
    });
});
