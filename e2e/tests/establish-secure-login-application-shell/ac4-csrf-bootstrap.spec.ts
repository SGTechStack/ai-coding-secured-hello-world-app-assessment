import { allure } from 'allure-playwright';
import { expect, test } from '../../fixtures/session';
import { acceptance, check } from '../../fixtures/acceptance';

const AC_DESCRIPTION = 'AC4: `GET /csrf` creates or reuses an anonymous server session and returns the CSRF token, canonical `X-CSRF-TOKEN` header name, and parameter name in the JSON response body.';

test(AC_DESCRIPTION, async ({ page, context }) => {
  await acceptance('US1: Establish the secure login application shell', AC_DESCRIPTION, 'Anonymous browser');
  await allure.description(AC_DESCRIPTION);
  await allure.attachment('Test plan', [
    'Data:',
    '- No account data. Discovery: GET /csrf returns JSON {token, headerName X-CSRF-TOKEN, parameterName}; the first call sets session cookie "id", a repeat call sets none and keeps its value.',
    'Isolation:',
    '- Isolated E2E stack: harness lifecycle (e2e/scripts/lifecycle.mjs) starts the backend with the local profile on a fresh in-memory H2 database; a fresh browser context starts with no cookies.',
    'Steps:',
    '1. Open /csrf in a fresh browser with no cookies.',
    '2. Read the JSON body the browser shows.',
    '3. Open /csrf again in the same browser.',
    '4. Compare the session cookie before and after.',
    'Visible outcomes:',
    '- the /csrf JSON body contains a non-empty token, headerName X-CSRF-TOKEN and a parameter name',
    '- a first /csrf call sets a session cookie and a repeat call in the same browser reuses that session',
    'Acceptance mapping:',
    '- AC4 | Story clause: returns the CSRF token, canonical `X-CSRF-TOKEN` header name, and parameter name in the JSON response body | Actor: Anonymous browser | Visible outcome: the /csrf JSON body contains a non-empty token, headerName X-CSRF-TOKEN and a parameter name',
    '- AC4 | Story clause: creates or reuses an anonymous server session | Actor: Anonymous browser | Visible outcome: a first /csrf call sets a session cookie and a repeat call in the same browser reuses that session',
    'Assumptions:',
    '- The token text is masked per response, so token equality is not used as reuse evidence; the unchanged session cookie is.',
  ].join('\n'), 'text/plain');

  const before = await context.cookies();
  const first = await page.goto('/csrf');
  const shown = await page.locator('body').innerText();
  const body = JSON.parse(shown) as { token?: string; headerName?: string; parameterName?: string };
  await check('Assertion: [AC4] actor: Anonymous browser | the /csrf JSON body contains a non-empty token, headerName X-CSRF-TOKEN and a parameter name',
    'token non-empty, headerName X-CSRF-TOKEN, parameterName non-empty',
    `token length ${body.token?.length ?? 0}, headerName ${body.headerName}, parameterName ${body.parameterName}`, async () => {
      expect(body.token ?? '').not.toBe('');
      expect(body.headerName).toBe('X-CSRF-TOKEN');
      expect(body.parameterName ?? '').not.toBe('');
    });

  const created = await context.cookies();
  const firstSetCookie = await first?.headerValue('set-cookie');
  const repeat = await page.goto('/csrf');
  const repeatSetCookie = await repeat?.headerValue('set-cookie');
  const reused = await context.cookies();
  const names = (list: Array<{ name: string }>) => list.map((c) => c.name).join(',') || 'none';
  await check('Assertion: [AC4] actor: Anonymous browser | a first /csrf call sets a session cookie and a repeat call in the same browser reuses that session',
    'no cookie before; the first call sets one session cookie; the repeat call sets none and the cookie value is unchanged',
    `before: ${names(before)}; first Set-Cookie=${!!firstSetCookie}, jar ${names(created)}; repeat Set-Cookie=${!!repeatSetCookie}, jar ${names(reused)}, same value=${created[0]?.value === reused[0]?.value}`, async () => {
      expect(before).toEqual([]);
      expect(firstSetCookie).toBeTruthy();
      expect(created).toHaveLength(1);
      expect(repeatSetCookie ?? null).toBeNull();
      expect(reused).toHaveLength(1);
      expect(reused[0].name).toBe(created[0].name);
      expect(reused[0].value).toBe(created[0].value);
    });
});
