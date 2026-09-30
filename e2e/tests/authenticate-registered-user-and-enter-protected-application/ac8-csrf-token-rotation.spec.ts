import { allure } from 'allure-playwright';
import type { Page } from '@playwright/test';
import { type Account, expect, test, logIn } from '../../fixtures/session';
import { acceptance, check } from '../../fixtures/acceptance';

const AC_DESCRIPTION = 'AC8: The CSRF token rotates after successful authentication and subsequent mutating requests use the new token.';
const AC8B_TITLE = AC_DESCRIPTION.replace(/^AC8:/, 'AC8b:');
const CREATED = 'Account created. You can now log in.';

type Csrf = { token: string; headerName: string };

const STORY = 'US2: Authenticate a registered user and enter the protected application';

/** The token body of the next GET /csrf response. */
const nextCsrf = (page: Page) =>
  page.waitForResponse((r) => new URL(r.url()).pathname === '/csrf').then(async (r) => await r.json() as Csrf);

/** Logs in through the UI and returns the anonymous token the login request used (rendering /login fetches none). */
async function signIn(page: Page, account: Account) {
  const csrf = nextCsrf(page);
  await logIn(page, account);
  return csrf;
}

const isRegisterPost = (url: URL) => url.pathname === '/api/auth/register';

/** From the signed-in landing, goes back in-app to /login and follows "Create an account" (client-side, so the SPA keeps its in-memory token). */
async function openRegistrationInApp(page: Page) {
  await page.goBack();
  await expect(page.getByRole('heading', { name: 'Log in' })).toBeVisible();
  await page.getByRole('link', { name: 'Create an account' }).click();
  await expect(page.getByRole('heading', { name: 'Create account' })).toBeVisible();
}

/** Fills and submits the registration form; returns the mutating request's CSRF header, status and problem code. */
async function submit(page: Page, headerName: string, username: string) {
  await page.getByLabel('Username').fill(username);
  await page.getByLabel('Email').fill(`${username}@example.com`);
  await page.getByLabel('Password', { exact: true }).fill('Correct-Horse-Battery-9');
  await page.getByLabel('Confirm password').fill('Correct-Horse-Battery-9');
  const request = page.waitForRequest((r) => isRegisterPost(new URL(r.url())) && r.method() === 'POST');
  const response = page.waitForResponse((r) => isRegisterPost(new URL(r.url())));
  await page.getByRole('button', { name: 'Create account', exact: true }).click();
  const sent = (await (await request).allHeaders())[headerName.toLowerCase()] ?? '';
  const settled = await response;
  const code = (await settled.json().catch(() => ({})) as { code?: string }).code ?? '';
  return { sent, status: settled.status(), code };
}

/** {@link submit}, plus the outcome the registration page renders. */
async function submitRegistration(page: Page, headerName: string, username: string) {
  const result = await submit(page, headerName, username);
  const outcome = page.getByRole('status').or(page.getByRole('alert')).first();
  await expect(outcome).toBeVisible();
  await outcome.scrollIntoViewIfNeeded();
  return { ...result, rendered: (await outcome.textContent()) ?? '' };
}

/** Full load of /register in the same signed-in browser: the SPA's memory is cleared, so it fetches this session's current token from /csrf. */
async function reloadAndRegister(page: Page, headerName: string, username: string) {
  const csrfResponse = page.waitForResponse((r) => new URL(r.url()).pathname === '/csrf');
  await page.goto('/register');
  await expect(page.getByRole('heading', { name: 'Create account' })).toBeVisible();
  const result = submitRegistration(page, headerName, username);
  const fresh = await (await csrfResponse).json() as Csrf;
  return { fresh, ...(await result) };
}

const suffix = () => Date.now().toString(36);

test(AC_DESCRIPTION, async ({ page, account }) => {
  await acceptance(STORY, AC_DESCRIPTION, 'Registered user');
  await allure.description(AC_DESCRIPTION);
  await allure.attachment('Test plan', [
    'Data:',
    '- Scenario-owned account (fixture registers it through POST /api/auth/register from a separate API context). Discovery: after sign-in the SPA fetches a new token from GET /csrf on its first mutating request, and it differs from the anonymous /csrf token; after sign-in the SPA can reach /register in-app (back to /login, link "Create an account") and its registration submission is a mutating request. Registration success copy "Account created. You can now log in." (frontend RegisterPage).',
    'Isolation:',
    '- Isolated E2E stack: harness lifecycle (e2e/scripts/lifecycle.mjs) starts the backend with the local profile on a fresh in-memory H2 database; this scenario owns its own browser session and a unique scenario-owned registration username (an accepted registration only adds that new account).',
    'Steps:',
    '1. Open /login, enter the scenario-owned username and password and select Log in.',
    '2. Go back to the login page and select "Create an account".',
    '3. Submit the registration form while the request carries the CSRF token issued before sign-in.',
    '4. Reload the registration page in the same signed-in browser and submit it again with the token the app now fetches for this session.',
    'Visible outcomes:',
    '- sign-in issues a different CSRF token and the pre-sign-in token is refused afterwards',
    'Acceptance mapping:',
    '- AC8 | Story clause: The CSRF token rotates after successful authentication | Actor: Registered user | Visible outcome: sign-in issues a different CSRF token and the pre-sign-in token is refused afterwards',
    'Assumptions:',
    '- The protected landing has no mutating action, so the post-sign-in mutating request is the SPA\'s own registration submission. The pre-sign-in token is placed on that request by a browser-side route (as in US1 AC5); the same-session control shows the session accepts a current token, so the refusal is due to rotation.',
  ].join('\n'), 'text/plain');

  const anonymous = await signIn(page, account);
  await openRegistrationInApp(page);
  const header = anonymous.headerName.toLowerCase();
  // Playwright reports the header the SPA attached, not a route override, so the route records what it forwarded.
  let forwarded = '';
  await page.route(isRegisterPost, (route) => {
    forwarded = anonymous.token;
    return route.continue({ headers: { ...route.request().headers(), [header]: forwarded } });
  });
  const csrfAfterSignIn = nextCsrf(page);
  const stale = { ...(await submit(page, anonymous.headerName, `e2eac8s${suffix()}`)), sent: forwarded };
  const rotated = await csrfAfterSignIn;
  // A refused token after sign-in is Session expired to the SPA: it clears its state and lands on /login.
  await expect(page.getByRole('heading', { name: 'Log in' })).toBeVisible();
  const landed = new URL(page.url()).pathname;
  await page.unroute(isRegisterPost);
  const control = await reloadAndRegister(page, anonymous.headerName, `e2eac8c${suffix()}`);
  await check('Assertion: [AC8] actor: Registered user | sign-in issues a different CSRF token and the pre-sign-in token is refused afterwards',
    `the /csrf token fetched after sign-in differs from the pre-sign-in one; registration carrying the pre-sign-in token is refused (CSRF_TOKEN_REJECTED, the SPA lands on /login) while the same session's current token is accepted ("${CREATED}")`,
    `pre-sign-in ${anonymous.token}; after sign-in ${rotated.token}; pre-sign-in token sent ${stale.sent === anonymous.token} -> ${stale.status} ${stale.code}, landed on ${landed}; same-session /csrf token sent ${control.sent === control.fresh.token} -> ${control.status} ${control.code}, rendered "${control.rendered}"`, async () => {
      expect(rotated.token).toBeTruthy();
      expect(rotated.token).not.toBe(anonymous.token);
      expect(stale.sent).toBe(anonymous.token);
      expect(stale.code).toBe('CSRF_TOKEN_REJECTED');
      expect(landed).toBe('/login');
      expect(control.sent).toBe(control.fresh.token);
      await expect(page.getByRole('status')).toHaveText(CREATED);
    });
});

test(AC8B_TITLE, async ({ page, account }) => {
  await acceptance(STORY, AC8B_TITLE, 'Registered user');
  await allure.description(AC_DESCRIPTION);
  await allure.attachment('Test plan', [
    'Data:',
    '- Scenario-owned account (fixture registers it through POST /api/auth/register from a separate API context). Discovery: login drops the anonymous token (startAuthenticatedCsrfSession) and the first mutating request after sign-in fetches the authenticated session\'s token from GET /csrf, then never re-fetches it; the registration submission carries that token, as does the same-session control with a token re-fetched from GET /csrf. Registration success copy "Account created. You can now log in." (frontend RegisterPage). Source: frontend/src/common/http/csrf.ts, backend LoginService.',
    'Isolation:',
    '- Isolated E2E stack: harness lifecycle (e2e/scripts/lifecycle.mjs) starts the backend with the local profile on a fresh in-memory H2 database; this scenario owns its own browser session and a unique scenario-owned registration username (an accepted registration only adds that new account).',
    'Steps:',
    '1. Open /login, enter the scenario-owned username and password and select Log in.',
    '2. Go back to the login page and select "Create an account".',
    '3. Fill in and submit the registration form: the app\'s next mutating request after sign-in.',
    '4. For comparison, reload the registration page in the same signed-in browser and submit again.',
    'Visible outcomes:',
    '- the next mutating request after sign-in uses the new token and is accepted',
    'Acceptance mapping:',
    '- AC8 | Story clause: subsequent mutating requests use the new token | Actor: Registered user | Visible outcome: the next mutating request after sign-in uses the new token and is accepted',
    'Assumptions:',
    '- "Use the new token" means the request carries it and it works: the registration shows "Account created. You can now log in.", as it does with the same session\'s re-fetched token.',
  ].join('\n'), 'text/plain');

  const anonymous = await signIn(page, account);
  await openRegistrationInApp(page);
  const csrfAfterSignIn = nextCsrf(page);
  const withRotated = await submitRegistration(page, anonymous.headerName, `e2eac8r${suffix()}`);
  const rotated = await csrfAfterSignIn;
  const control = await reloadAndRegister(page, rotated.headerName, `e2eac8f${suffix()}`);
  await check('Assertion: [AC8] actor: Registered user | the next mutating request after sign-in uses the new token and is accepted',
    `registration after sign-in carries the freshly fetched /csrf token (not the pre-sign-in one) and shows "${CREATED}", as the reloaded same-session token does`,
    `post-sign-in /csrf token ${rotated.token} sent ${withRotated.sent === rotated.token} -> ${withRotated.status} ${withRotated.code}, rendered "${withRotated.rendered}"; same-session /csrf token sent ${control.sent === control.fresh.token} -> ${control.status} ${control.code}, rendered "${control.rendered}"`, async () => {
      expect(rotated.token).not.toBe(anonymous.token);
      expect(withRotated.sent).toBe(rotated.token);
      expect(control.sent).toBe(control.fresh.token);
      expect(control.rendered).toBe(CREATED);
      expect(withRotated.rendered).toBe(CREATED);
    });
});
