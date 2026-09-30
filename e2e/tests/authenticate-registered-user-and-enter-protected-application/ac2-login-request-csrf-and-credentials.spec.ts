import { allure } from 'allure-playwright';
import { expect, test, greeting, cookieValue } from '../../fixtures/session';
import { acceptance, check } from '../../fixtures/acceptance';

const AC_DESCRIPTION = 'AC2: The real login request proves that the in-memory anonymous CSRF token is attached using the server-advertised header name (`X-CSRF-TOKEN`) while session credentials are included.';

test(AC_DESCRIPTION, async ({ page, context, account }) => {
  await acceptance('US2: Authenticate a registered user and enter the protected application', AC_DESCRIPTION, 'Registered user');
  await allure.description(AC_DESCRIPTION);
  await allure.attachment('Test plan', [
    'Data:',
    '- Scenario-owned account (fixture registers it through POST /api/auth/register from a separate API context, so the browser starts cookie-free). Discovery: rendering /login sets no cookie; on submit the SPA calls GET /csrf (body {token, headerName:"X-CSRF-TOKEN", parameterName:"_csrf"}, Set-Cookie id=<anonymous session>) and then POST /api/auth/login carrying that token in x-csrf-token and Cookie id=<same value>.',
    'Isolation:',
    '- Isolated E2E stack: harness lifecycle (e2e/scripts/lifecycle.mjs) starts the backend with the local profile on a fresh in-memory H2 database; this scenario owns its own browser session.',
    'Steps:',
    '1. Open /login.',
    '2. Enter the scenario-owned username and password.',
    '3. Select Log in.',
    '4. Compare the real login request\'s headers with what the server advertised in its CSRF bootstrap response.',
    'Visible outcomes:',
    '- the real login request carries the anonymous CSRF token in the header the server advertised (X-CSRF-TOKEN)',
    '- the real login request includes the anonymous session cookie the server issued with that token',
    'Acceptance mapping:',
    '- AC2 | Story clause: the in-memory anonymous CSRF token is attached using the server-advertised header name (`X-CSRF-TOKEN`) | Actor: Registered user | Visible outcome: the real login request carries the anonymous CSRF token in the header the server advertised (X-CSRF-TOKEN)',
    '- AC2 | Story clause: while session credentials are included | Actor: Registered user | Visible outcome: the real login request includes the anonymous session cookie the server issued with that token',
    'Assumptions:',
    '- "In-memory" storage of the anonymous token was proved in US1; here the claim is its attachment to the real login request.',
  ].join('\n'), 'text/plain');

  await page.goto('/login');
  const cookiesOnArrival = (await context.cookies()).map((c) => c.name);
  await page.getByLabel('Username').fill(account.username);
  await page.getByLabel('Password').fill(account.password);
  const csrfResponse = page.waitForResponse((r) => new URL(r.url()).pathname === '/csrf');
  const loginRequest = page.waitForRequest((r) => new URL(r.url()).pathname === '/api/auth/login' && r.method() === 'POST');
  const loginResponse = page.waitForResponse((r) => new URL(r.url()).pathname === '/api/auth/login');
  await page.getByRole('button', { name: 'Log in', exact: true }).click();
  const csrf = await csrfResponse;
  const csrfBody = await csrf.json() as { token: string; headerName: string };
  const issuedSession = cookieValue(await csrf.headerValue('set-cookie'), 'id');
  const request = await loginRequest;
  const headers = await request.allHeaders();
  await loginResponse;
  await expect(greeting(page, account.username)).toBeVisible();
  const sentToken = headers[csrfBody.headerName.toLowerCase()];
  const sentSession = cookieValue(headers.cookie, 'id');

  await check('Assertion: [AC2] actor: Registered user | the real login request carries the anonymous CSRF token in the header the server advertised (X-CSRF-TOKEN)',
    'advertised header name X-CSRF-TOKEN; login request header X-CSRF-TOKEN equals the /csrf body token',
    `advertised "${csrfBody.headerName}"; /csrf token ${csrfBody.token}; login request ${csrfBody.headerName}: ${sentToken ?? '(absent)'}`, async () => {
      expect(csrfBody.headerName).toBe('X-CSRF-TOKEN');
      expect(sentToken).toBe(csrfBody.token);
    });
  await check('Assertion: [AC2] actor: Registered user | the real login request includes the anonymous session cookie the server issued with that token',
    'no cookie before submit; login request Cookie id equals the id issued by /csrf',
    `cookies on arrival [${cookiesOnArrival.join(',')}]; /csrf issued id=${issuedSession ?? '(none)'}; login request Cookie id=${sentSession ?? '(absent)'}`, async () => {
      expect(cookiesOnArrival).toEqual([]);
      expect(issuedSession).toBeTruthy();
      expect(sentSession).toBe(issuedSession);
    });
});
