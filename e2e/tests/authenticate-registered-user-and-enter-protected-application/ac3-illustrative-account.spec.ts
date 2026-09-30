import { allure } from 'allure-playwright';
import { expect, test, enterCredentials, greeting, SEED_USER } from '../../fixtures/session';
import { acceptance, check } from '../../fixtures/acceptance';

const AC_DESCRIPTION = 'AC3: The illustrative `johndoe` / `Password123!` account is available only in development and test and is not special-cased by authentication logic.';
const AC3B_TITLE = AC_DESCRIPTION.replace(/^AC3:/, 'AC3b:');

test(AC_DESCRIPTION, async ({ page }) => {
  await acceptance('US2: Authenticate a registered user and enter the protected application', AC_DESCRIPTION, 'Registered user johndoe');
  await allure.description(AC_DESCRIPTION);
  await allure.attachment('Test plan', [
    'Data:',
    '- Illustrative account johndoe / Password123! seeded by the local (development/test) profile. Discovery: it signs in on a fresh local-profile stack and the SPA shows the heading "Protected application".',
    'Isolation:',
    '- Isolated E2E stack: harness lifecycle (e2e/scripts/lifecycle.mjs) starts the backend with the local profile on a fresh in-memory H2 database; this scenario owns its own browser session.',
    'Steps:',
    '1. Open /login on the local (development/test) E2E stack.',
    '2. Enter username johndoe and password Password123!.',
    '3. Select Log in.',
    '4. Read the page the browser lands on.',
    'Visible outcomes:',
    '- the illustrative johndoe account signs in on the development/test stack and reaches the protected application',
    'Acceptance mapping:',
    '- AC3 | Story clause: The illustrative `johndoe` / `Password123!` account is available | Actor: Registered user johndoe | Visible outcome: the illustrative johndoe account signs in on the development/test stack and reaches the protected application',
    'Assumptions:',
    '- The "only in development and test" and "not special-cased" halves are private backend facts; they are a separate skipped AC3b scenario.',
  ].join('\n'), 'text/plain');

  await enterCredentials(page, SEED_USER.username, SEED_USER.password);
  await page.getByRole('button', { name: 'Log in', exact: true }).click();
  const heading = greeting(page, 'johndoe');
  await expect(heading).toBeVisible();
  await heading.scrollIntoViewIfNeeded();
  await check('Assertion: [AC3] actor: Registered user johndoe | the illustrative johndoe account signs in on the development/test stack and reaches the protected application',
    'URL path /home with the Home page heading "Hello, johndoe"',
    `URL path ${new URL(page.url()).pathname}; heading "${await heading.textContent()}"`, async () => {
      await expect(page).toHaveURL(/\/home$/);
      await expect(heading).toBeVisible();
    });
});

test(AC3B_TITLE, async () => {
  await acceptance('US2: Authenticate a registered user and enter the protected application', AC3B_TITLE, 'Operator');
  await allure.description(`${AC_DESCRIPTION}\n\nBlocked: the story requires the johndoe account to exist only in development and test and to get no special handling in the sign-in code. The E2E harness can only start the local (development) profile, and neither a production profile nor the authentication source is visible from a browser, so this cannot be shown here; it belongs to backend configuration and integration tests.`);
  await allure.attachment('Test plan', [
    'Data:',
    '- No browser data. Discovery: e2e/scripts/lifecycle.mjs always spawns spring-boot:run with -Dspring-boot.run.profiles=local; the seed lives under backend local resources.',
    'Isolation:',
    '- Isolated E2E stack: harness lifecycle (e2e/scripts/lifecycle.mjs), local profile, fresh in-memory H2; nothing is mutated.',
    'Steps:',
    '1. Start the application in a non-development, non-test profile.',
    '2. Try to sign in as johndoe / Password123!.',
    '3. Confirm the account is absent and that sign-in treats johndoe like any other account.',
    'Visible outcomes:',
    '- outside development and test the johndoe account does not exist, and sign-in has no johndoe-specific handling',
    'Acceptance mapping:',
    '- AC3 | Story clause: available only in development and test and is not special-cased by authentication logic | Actor: Operator | Visible outcome: outside development and test the johndoe account does not exist, and sign-in has no johndoe-specific handling',
  ].join('\n'), 'text/plain');
  await allure.attachment('blocker evidence', [
    'Clause: "available only in development and test and is not special-cased by authentication logic".',
    'Blocked prerequisite: a running non-development/non-test profile. e2e/scripts/lifecycle.mjs spawns only `mvn spring-boot:run -Dspring-boot.run.profiles=local`; the harness offers no other profile, and a spec must not start or reconfigure services.',
    'The absence of a johndoe branch in authentication logic is a source-code property with no browser surface.',
    'Where it is proved: backend configuration/integration tests (profile-scoped seed) and code review of the authentication path.',
  ].join('\n'), 'text/plain');
  test.skip(true, 'Only the local profile runs under the E2E harness.');
});
