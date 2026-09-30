import { allure } from 'allure-playwright';
import { expect, test, enterCredentials, greeting } from '../../fixtures/session';
import { acceptance, check } from '../../fixtures/acceptance';

const AC_DESCRIPTION = 'AC9: The client seeds the session query directly from the login response, invalidates the router, and navigates to protected `/` without calling a current-user endpoint.';

test(AC_DESCRIPTION, async ({ page, account }) => {
  await acceptance('US2: Authenticate a registered user and enter the protected application', AC_DESCRIPTION, 'Registered user');
  await allure.description(AC_DESCRIPTION);
  await allure.attachment('Test plan', [
    'Data:',
    '- Scenario-owned account (fixture registers it through POST /api/auth/register from a separate API context, before the browser starts recording). Discovery: after submit the browser requests GET /csrf, POST /api/auth/login and the lazy route chunk /assets/_authenticated.index-*.js only; it navigates client-side to / whose Home page reads its Greeting (GET /api/hello); no other /api/** request follows the login POST.',
    'Isolation:',
    '- Isolated E2E stack: harness lifecycle (e2e/scripts/lifecycle.mjs) starts the backend with the local profile on a fresh in-memory H2 database; this scenario owns its own browser session.',
    'Steps:',
    '1. Open /login.',
    '2. Enter the scenario-owned username and password and select Log in.',
    '3. Watch the page the browser lands on and every request it sends after sign-in.',
    'Visible outcomes:',
    '- after sign-in the browser goes straight to /home showing the protected application',
    '- no current-user (or any other API) request besides the Greeting is sent after the login request',
    'Acceptance mapping:',
    '- AC9 | Story clause: navigates to protected `/` | Actor: Registered user | Visible outcome: after sign-in the browser goes straight to /home showing the protected application',
    '- AC9 | Story clause: without calling a current-user endpoint | Actor: Registered user | Visible outcome: no current-user (or any other API) request is sent after the login request',
    'Assumptions:',
    '- Seeding the session query and invalidating the router are internal; their user-visible effect is that the protected route guard admits the user immediately, with the login response as the only API data. The username shown on the landing comes from the server Greeting, not from the seeded profile.',
  ].join('\n'), 'text/plain');

  const requests: string[] = [];
  page.on('request', (request) => requests.push(`${request.method()} ${new URL(request.url()).pathname}`));
  await enterCredentials(page, account.username, account.password);
  const loginResponse = page.waitForResponse((r) => new URL(r.url()).pathname === '/api/auth/login');
  await page.getByRole('button', { name: 'Log in', exact: true }).click();
  await loginResponse;
  const heading = greeting(page, account.username);
  await expect(heading).toBeVisible();
  await page.waitForLoadState('networkidle');
  await heading.scrollIntoViewIfNeeded();

  const loginIndex = requests.indexOf('POST /api/auth/login');
  const afterLogin = requests.slice(loginIndex + 1);
  // The Home page's own Greeting read is page content, not a current-user lookup that restores the session.
  const apiAfterLogin = afterLogin.filter((entry) => entry !== 'GET /api/hello')
    .filter((entry) => /\s\/api\//.test(entry) || /\/(me|current-?user|session|csrf)$/i.test(entry));
  await check('Assertion: [AC9] actor: Registered user | after sign-in the browser goes straight to /home showing the protected application',
    `URL path /home with the Home page heading "Hello, ${account.username}"`,
    `URL path ${new URL(page.url()).pathname}; heading "${await heading.textContent()}"`, async () => {
      await expect(page).toHaveURL(/\/home$/);
      await expect(heading).toBeVisible();
    });
  await check('Assertion: [AC9] actor: Registered user | no current-user (or any other API) request is sent after the login request',
    'login request sent; no API/current-user request after it',
    `requests after login: [${afterLogin.join(', ')}]`, async () => {
      expect(loginIndex).toBeGreaterThanOrEqual(0);
      expect(apiAfterLogin).toEqual([]);
    });
});
