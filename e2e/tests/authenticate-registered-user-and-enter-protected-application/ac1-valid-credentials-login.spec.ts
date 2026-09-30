import { allure } from 'allure-playwright';
import { expect, test, enterCredentials, greeting } from '../../fixtures/session';
import { acceptance, check } from '../../fixtures/acceptance';

const AC_DESCRIPTION = 'AC1: Submitting valid credentials to the versioned login endpoint authenticates the user and returns the session profile needed by the client.';

test(AC_DESCRIPTION, async ({ page, account }) => {
  await acceptance('US2: Authenticate a registered user and enter the protected application', AC_DESCRIPTION, 'Registered user');
  await allure.description(AC_DESCRIPTION);
  await allure.attachment('Test plan', [
    'Data:',
    '- Scenario-owned account (fixture registers it through POST /api/auth/register). Discovery: submitting it on /login sends POST /api/auth/login, which answers {profile:{id, username}, csrf:{...}}, and the SPA renders the Home page at / with the Greeting "Hello, <username>" as its heading.',
    'Isolation:',
    '- Isolated E2E stack: harness lifecycle (e2e/scripts/lifecycle.mjs) starts the backend with the local profile on a fresh in-memory H2 database; this scenario owns its account and its browser session.',
    'Steps:',
    '1. Open /login.',
    '2. Enter the scenario-owned username and password.',
    '3. Select Log in.',
    '4. Read the page the browser lands on and the profile the login endpoint returned.',
    'Visible outcomes:',
    '- after signing in with valid credentials the protected application page is shown',
    '- the versioned login endpoint returns the signed-in user\'s profile (id and username)',
    'Acceptance mapping:',
    '- AC1 | Story clause: Submitting valid credentials to the versioned login endpoint authenticates the user | Actor: Registered user | Visible outcome: after signing in with valid credentials the protected application page is shown',
    '- AC1 | Story clause: returns the session profile needed by the client | Actor: Registered user | Visible outcome: the versioned login endpoint returns the signed-in user\'s profile (id and username)',
    'Assumptions:',
    '- The username on the landing page comes from the server Greeting, so the returned profile is read from the browser-received login response body (the named surface of the clause).',
  ].join('\n'), 'text/plain');

  await enterCredentials(page, account.username, account.password);
  const loginResponse = page.waitForResponse((r) => new URL(r.url()).pathname === '/api/auth/login' && r.request().method() === 'POST');
  await page.getByRole('button', { name: 'Log in', exact: true }).click();
  const response = await loginResponse;
  const body = await response.json() as { profile?: { id?: string; username?: string } };
  const heading = greeting(page, account.username);
  await expect(heading).toBeVisible();
  await heading.scrollIntoViewIfNeeded();

  await check('Assertion: [AC1] actor: Registered user | after signing in with valid credentials the protected application page is shown',
    `URL path /home with the Home page heading "Hello, ${account.username}"`,
    `URL path ${new URL(page.url()).pathname}; heading "${await heading.textContent()}"; login response ${response.status()}`, async () => {
      await expect(page).toHaveURL(/\/home$/);
      await expect(heading).toBeVisible();
    });
  await check('Assertion: [AC1] actor: Registered user | the versioned login endpoint returns the signed-in user\'s profile (id and username)',
    `POST /api/auth/login body profile.username "${account.username}" and a non-empty profile.id`,
    `${new URL(response.url()).pathname} profile ${JSON.stringify(body.profile)}`, async () => {
      expect(body.profile?.username).toBe(account.username);
      expect(typeof body.profile?.id).toBe('string');
      expect(body.profile?.id).not.toBe('');
    });
});
