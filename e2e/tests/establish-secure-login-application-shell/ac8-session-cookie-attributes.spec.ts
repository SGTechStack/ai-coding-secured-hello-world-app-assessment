import { allure } from 'allure-playwright';
import { expect, test, enterCredentials, greeting, SEED_USER } from '../../fixtures/session';
import { acceptance, check } from '../../fixtures/acceptance';

const AC_DESCRIPTION = 'AC8: Session cookies use `HttpOnly`, `Secure`, and `SameSite=Strict` attributes in secure environments.';

test(AC_DESCRIPTION, async ({ page, context }) => {
  await acceptance('US1: Establish the secure login application shell', AC_DESCRIPTION, 'Anonymous browser, then registered user (local seed johndoe)');
  await allure.description(AC_DESCRIPTION);
  await allure.attachment('Test plan', [
    'Data:',
    '- Local-profile seed account johndoe / Password123! (e2e/README.md, permitted test oracle). Discovery: over HTTPS, /csrf sets session cookie "id" with Secure; HttpOnly; SameSite=Strict.',
    'Isolation:',
    '- Isolated E2E stack: harness lifecycle (e2e/scripts/lifecycle.mjs) starts the backend with the local profile over HTTPS on a fresh in-memory H2 database; only this browser context signs in.',
    'Steps:',
    '1. Open /login over HTTPS.',
    '2. Enter the registered credentials and select Log in (the app first creates the anonymous session).',
    '3. Wait for the protected application.',
    '4. Inspect every session cookie the browser holds.',
    'Visible outcomes:',
    '- the session cookie is HttpOnly, Secure and SameSite=Strict',
    'Acceptance mapping:',
    '- AC8 | Story clause: Session cookies use `HttpOnly`, `Secure`, and `SameSite=Strict` attributes | Actor: Anonymous browser | Visible outcome: the session cookie is HttpOnly, Secure and SameSite=Strict',
    'Assumptions:',
    '- The HTTPS E2E stack is the "secure environment"; both the anonymous and the authenticated session cookie are checked.',
  ].join('\n'), 'text/plain');

  await enterCredentials(page, SEED_USER.username, SEED_USER.password);
  const csrfResponse = page.waitForResponse((r) => new URL(r.url()).pathname === '/csrf');
  await page.getByRole('button', { name: 'Log in', exact: true }).click();
  const setCookie = (await (await csrfResponse).headerValue('set-cookie')) ?? '';
  const heading = greeting(page, 'johndoe');
  await expect(heading).toBeVisible();
  await heading.scrollIntoViewIfNeeded();
  const jar = await context.cookies();
  await check('Assertion: [AC8] actor: Anonymous browser | the session cookie is HttpOnly, Secure and SameSite=Strict',
    'the anonymous session Set-Cookie and every cookie held after login are HttpOnly, Secure and SameSite=Strict',
    `anonymous Set-Cookie "${setCookie.replace(/=[^;]*/, '=<redacted>')}"; jar ${jar.map((c) => `${c.name}(httpOnly=${c.httpOnly}, secure=${c.secure}, sameSite=${c.sameSite})`).join(', ')}`, async () => {
      expect(setCookie).toMatch(/;\s*Secure\b/i);
      expect(setCookie).toMatch(/;\s*HttpOnly\b/i);
      expect(setCookie).toMatch(/;\s*SameSite=Strict\b/i);
      expect(jar.length).toBeGreaterThan(0);
      for (const cookie of jar) {
        expect({ name: cookie.name, httpOnly: cookie.httpOnly, secure: cookie.secure, sameSite: cookie.sameSite })
          .toEqual({ name: cookie.name, httpOnly: true, secure: true, sameSite: 'Strict' });
      }
    });
});
