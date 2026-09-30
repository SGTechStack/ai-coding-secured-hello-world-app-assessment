import { allure } from 'allure-playwright';
import { expect, test } from '../../fixtures/session';
import { acceptance, check } from '../../fixtures/acceptance';

const AC_DESCRIPTION = 'AC2: Visiting `/login` renders a responsive login form with labelled username and password fields and a `Log in` button.';

test(AC_DESCRIPTION, async ({ page }) => {
  await acceptance('US1: Establish the secure login application shell', AC_DESCRIPTION, 'Anonymous visitor');
  await allure.description(AC_DESCRIPTION);
  await allure.attachment('Test plan', [
    'Data:',
    '- No account data. Discovery (live Chromium probe): <label for=username>Username</label>, <label for=password>Password</label>, one submit button "Log in".',
    'Isolation:',
    '- Isolated E2E stack: harness lifecycle (e2e/scripts/lifecycle.mjs) starts the backend with the local profile on a fresh in-memory H2 database; nothing shared is mutated.',
    'Steps:',
    '1. Open /login.',
    '2. Find the Username and Password fields by their labels.',
    '3. Find the Log in button.',
    'Visible outcomes:',
    '- Username and Password inputs are reachable by their accessible labels',
    '- a button named Log in is visible',
    'Acceptance mapping:',
    '- AC2 | Story clause: labelled username and password fields | Actor: Anonymous visitor | Visible outcome: Username and Password inputs are reachable by their accessible labels',
    '- AC2 | Story clause: a `Log in` button | Actor: Anonymous visitor | Visible outcome: a button named Log in is visible',
    'Assumptions:',
    '- "Responsive" layout behaviour is proved by the AC3 viewport scenarios, not repeated here.',
  ].join('\n'), 'text/plain');

  await page.goto('/login');
  const username = page.getByLabel('Username');
  const password = page.getByLabel('Password');
  await username.scrollIntoViewIfNeeded();
  const observedFields = `Username visible=${await username.isVisible()} type=${await username.getAttribute('type')}; Password visible=${await password.isVisible()} type=${await password.getAttribute('type')}`;
  await check('Assertion: [AC2] actor: Anonymous visitor | Username and Password inputs are reachable by their accessible labels',
    'visible editable Username field and masked Password field found by label', observedFields, async () => {
      await expect(username).toBeVisible();
      await expect(username).toBeEditable();
      await expect(password).toBeVisible();
      await expect(password).toBeEditable();
      await expect(password).toHaveAttribute('type', 'password');
    });
  const button = page.getByRole('button', { name: 'Log in', exact: true });
  await button.scrollIntoViewIfNeeded();
  await check('Assertion: [AC2] actor: Anonymous visitor | a button named Log in is visible',
    'button "Log in"', `buttons: ${JSON.stringify(await page.getByRole('button').allTextContents())}`, async () => {
      await expect(button).toBeVisible();
    });
});
