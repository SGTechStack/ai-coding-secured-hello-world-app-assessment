import { allure } from 'allure-playwright';
import { expect, test } from '../../fixtures/session';
import { acceptance, check } from '../../fixtures/acceptance';

const AC_DESCRIPTION = 'AC9: Public access is limited to the login and CSRF bootstrap surfaces; other application routes deny anonymous access by default.';

// API routes answer 401 whether or not they exist; any other address loads the app, whose router decides (ADR 0007).
const NON_PUBLIC_ROUTES: Record<string, number> = { '/api/auth/me': 401, '/api/anything': 401 };

test(AC_DESCRIPTION, async ({ page }) => {
  await acceptance('US1: Establish the secure login application shell', AC_DESCRIPTION, 'Anonymous visitor');
  await allure.description(AC_DESCRIPTION);
  await allure.attachment('Test plan', [
    'Data:',
    '- No account data. Discovery: anonymous / loads the app, whose Session check (GET /api/profile) is refused with 401, and it lands on /login; in-app navigation to / lands on /login; anonymous /api/auth/me and /api/anything return 401; /actuator/health is the public load balancer health check and answers only its status; /login and /csrf succeed.',
    'Isolation:',
    '- Isolated E2E stack: harness lifecycle (e2e/scripts/lifecycle.mjs) starts the backend with the local profile on a fresh in-memory H2 database; nothing shared is mutated.',
    'Steps:',
    '1. Open the protected application address / without signing in.',
    '2. Open /login and navigate to / inside the app.',
    '3. Open non-public API routes without signing in.',
    '4. Open the login page and the CSRF bootstrap address without signing in.',
    'Visible outcomes:',
    '- an anonymous visit to the protected application route is denied without showing protected content',
    '- an anonymous request to a non-public API route is refused while /login and /csrf succeed',
    'Acceptance mapping:',
    '- AC9 | Story clause: other application routes deny anonymous access by default | Actor: Anonymous visitor | Visible outcome: an anonymous visit to the protected application route is denied without showing protected content',
    '- AC9 | Story clause: Public access is limited to the login and CSRF bootstrap surfaces | Actor: Anonymous visitor | Visible outcome: an anonymous request to a non-public API route is refused while /login and /csrf succeed',
    'Assumptions:',
    '- /register is a public surface explicitly added later by the separate Registration feature spec ("explicitly permitted by the security policy"); it is a documented exception outside this story and is not probed here.',
  ].join('\n'), 'text/plain');

  const sessionCheck = page.waitForResponse((r) => new URL(r.url()).pathname === '/api/profile');
  await page.goto('/');
  const refused = await sessionCheck;
  const directLogin = page.getByRole('heading', { name: 'Log in' });
  await expect(directLogin).toBeVisible();
  await check('Assertion: [AC9] actor: Anonymous visitor | an anonymous visit to the protected application route is denied without showing protected content',
    'direct visit to / is refused its Session check (401) and lands on the Log in page with no protected content',
    `GET /api/profile -> ${refused.status()}; landed at ${new URL(page.url()).pathname}`, async () => {
      expect(refused.status()).toBe(401);
      await expect(page).toHaveURL(/\/login$/);
      await expect(directLogin).toBeVisible();
      await expect(page.getByRole('heading', { name: /^Hello, / })).toHaveCount(0);
    });

  await page.goto('/login');
  await expect(page.getByRole('heading', { name: 'Log in' })).toBeVisible();
  await page.evaluate(() => { history.pushState({}, '', '/'); dispatchEvent(new PopStateEvent('popstate')); });
  const loginHeading = page.getByRole('heading', { name: 'Log in' });
  await expect(page).toHaveURL(/\/login$/);
  await loginHeading.scrollIntoViewIfNeeded();
  await check('Assertion: [AC9] actor: Anonymous visitor | an anonymous visit to the protected application route is denied without showing protected content',
    'in-app navigation to / returns to the Log in page', `url ${new URL(page.url()).pathname}; protected headings ${await page.getByRole('heading', { name: /^Hello, / }).count()}`, async () => {
      await expect(page).toHaveURL(/\/login$/);
      await expect(loginHeading).toBeVisible();
      await expect(page.getByRole('heading', { name: /^Hello, / })).toHaveCount(0);
    });

  const observed: string[] = [];
  const statuses: Record<string, number> = {};
  for (const path of [...Object.keys(NON_PUBLIC_ROUTES), '/login', '/csrf']) {
    const response = await page.goto(path);
    const first = (await response?.request().redirectedFrom()?.response()) ?? response;
    statuses[path] = first?.status() ?? 0;
    observed.push(`${path} ${statuses[path]} ${response?.headers()['content-type'] ?? ''}`.trim());
  }
  const csrfShown = await page.locator('body').innerText();
  // The load balancer's health check is public by design and reveals nothing but its status.
  const health = await page.request.get('/actuator/health');
  const healthBody = await health.text();
  await check('Assertion: [AC9] actor: Anonymous visitor | an anonymous request to a non-public API route is refused while /login and /csrf succeed',
    'every non-public API route is refused with 401 Unauthorized and /actuator/health answers only its status; /login and /csrf succeed and /csrf shows its token JSON', `${observed.join('; ')}; /actuator/health ${health.status()} ${healthBody}`, async () => {
      for (const [path, status] of Object.entries(NON_PUBLIC_ROUTES)) expect(statuses[path], path).toBe(status);
      expect(statuses['/login']).toBe(200);
      expect(statuses['/csrf']).toBe(200);
      expect(csrfShown).toContain('"headerName"');
      expect(health.status()).toBe(200);
      expect(JSON.parse(healthBody)).toEqual({ status: 'UP' });
    });
});
