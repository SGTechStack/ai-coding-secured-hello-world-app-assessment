import { allure } from 'allure-playwright';
import { expect, test } from '../../fixtures/session';
import { acceptance, check } from '../../fixtures/acceptance';

const AC_DESCRIPTION = 'AC1: The Spring Boot backend and React SPA start successfully using the workspace-provided Java, Maven, and Node.js runtimes.';

test(AC_DESCRIPTION, async ({ page }) => {
  await acceptance('US1: Establish the secure login application shell', AC_DESCRIPTION, 'Anonymous visitor');
  await allure.description(AC_DESCRIPTION);
  await allure.attachment('Test plan', [
    'Data:',
    '- No account data. Discovery: harness lifecycle start became healthy and GET /login served the SPA with the heading "Log in".',
    'Isolation:',
    '- Isolated E2E stack: harness lifecycle (e2e/scripts/lifecycle.mjs) starts the backend with the local profile on a fresh in-memory H2 database; nothing shared is mutated.',
    'Steps:',
    '1. Let the harness start the backend and SPA.',
    '2. Open /login in the browser.',
    '3. Read the rendered login page heading.',
    'Visible outcomes:',
    '- the SPA login page is served by the running backend at the base URL',
    'Acceptance mapping:',
    '- AC1 | Story clause: The Spring Boot backend and React SPA start successfully | Actor: Anonymous visitor | Visible outcome: the SPA login page is served by the running backend at the base URL',
    'Assumptions:',
    '- Which JDK/Maven/Node binaries run the stack is harness-owned setup (lifecycle.mjs pins the workspace runtimes); only the served SPA is browser-observable.',
  ].join('\n'), 'text/plain');

  const response = await page.goto('/login');
  const heading = page.getByRole('heading', { name: 'Log in' });
  await heading.scrollIntoViewIfNeeded();
  const observed = `document ${response?.status()} ${response?.headers()['content-type']}; heading "${await heading.textContent()}" at ${new URL(page.url()).origin}`;
  await check('Assertion: [AC1] actor: Anonymous visitor | the SPA login page is served by the running backend at the base URL',
    'React-rendered heading "Log in" on /login', observed, async () => {
      expect(response?.ok()).toBe(true);
      await expect(heading).toBeVisible();
    });
});
