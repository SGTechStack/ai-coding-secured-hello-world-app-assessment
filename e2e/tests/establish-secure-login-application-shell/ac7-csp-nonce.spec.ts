import { allure } from 'allure-playwright';
import { expect, test } from '../../fixtures/session';
import { acceptance, check } from '../../fixtures/acceptance';

const AC_DESCRIPTION = "AC7: Missing or unreplaced CSP nonces fail frontend bootstrap safely; the served document uses the workspace's strict nonce-based CSP policy.";
const BOOTSTRAP_TITLE = AC_DESCRIPTION.replace(/^AC7:/, 'AC7b:');

const directivesOf = (policy: string) => Object.fromEntries(
  policy.split(';').map((d) => d.trim().split(/\s+/)).filter((parts) => parts[0]).map(([name, ...values]) => [name, values]),
) as Record<string, string[]>;
const nonceOf = (policy: string) => (directivesOf(policy)['script-src'] ?? []).find((s) => s.startsWith("'nonce-"))?.slice(7, -1) ?? '';

test(AC_DESCRIPTION, async ({ page, cspViolations }) => {
  await acceptance('US1: Establish the secure login application shell', AC_DESCRIPTION, 'Anonymous browser');
  await allure.description(AC_DESCRIPTION);
  await allure.attachment('Test plan', [
    'Data:',
    '- No account data. Discovery: the /login CSP header has default-src \'self\', base-uri \'none\', object-src \'none\', script-src \'self\' \'nonce-<value>\'; every script and the csp-nonce marker carry that nonce; the nonce changes per response.',
    'Isolation:',
    '- Isolated E2E stack: harness lifecycle (e2e/scripts/lifecycle.mjs) starts the backend with the local profile on a fresh in-memory H2 database; nothing shared is mutated.',
    'Steps:',
    '1. Open /login.',
    '2. Read the Content-Security-Policy of the served page.',
    '3. Compare the page script nonces with the policy.',
    '4. Reload and confirm a new nonce and a rendered login form with no CSP errors.',
    'Visible outcomes:',
    '- the /login document carries a nonce-based Content-Security-Policy header whose nonce matches the page scripts and no CSP violation is logged',
    'Acceptance mapping:',
    '- AC7 | Story clause: the served document uses the workspace\'s strict nonce-based CSP policy | Actor: Anonymous browser | Visible outcome: the /login document carries a nonce-based Content-Security-Policy header whose nonce matches the page scripts and no CSP violation is logged',
    'Assumptions:',
    '- "Strict" is checked as: script-src allows only self plus a per-response nonce, no unsafe-inline, unsafe-eval or wildcard, and object-src and base-uri are none. The AC7b scenario covers the missing/unreplaced nonce clause.',
  ].join('\n'), 'text/plain');

  const first = await page.goto('/login');
  const policy = (await first?.headerValue('content-security-policy')) ?? '';
  const directives = directivesOf(policy);
  const nonce = nonceOf(policy);
  await expect(page.getByRole('heading', { name: 'Log in' })).toBeVisible();
  const pageNonces = await page.evaluate(() => ({
    scripts: [...document.querySelectorAll('script')].map((s) => s.nonce),
    marker: document.querySelector<HTMLMetaElement>('meta[property="csp-nonce"]')?.nonce ?? '',
  }));
  const second = await page.goto('/login');
  const secondNonce = nonceOf((await second?.headerValue('content-security-policy')) ?? '');
  const heading = page.getByRole('heading', { name: 'Log in' });
  await expect(heading).toBeVisible();
  await heading.scrollIntoViewIfNeeded();
  await check('Assertion: [AC7] actor: Anonymous browser | the /login document carries a nonce-based Content-Security-Policy header whose nonce matches the page scripts and no CSP violation is logged',
    'strict nonce CSP; every script and the csp-nonce marker carry the header nonce; a new nonce per response; no CSP console error',
    `policy "${policy.replaceAll(nonce, '<nonce>')}"; ${pageNonces.scripts.length} scripts, all match=${pageNonces.scripts.every((n) => n === nonce)}; marker matches=${pageNonces.marker === nonce}; new nonce on reload=${secondNonce !== '' && secondNonce !== nonce}; CSP errors ${cspViolations.length}`, async () => {
      expect(nonce.length).toBeGreaterThanOrEqual(16);
      expect(directives['default-src']).toEqual(["'self'"]);
      expect(directives['object-src']).toEqual(["'none'"]);
      expect(directives['base-uri']).toEqual(["'none'"]);
      expect(directives['script-src']).toEqual(["'self'", `'nonce-${nonce}'`]);
      expect(policy).not.toMatch(/unsafe-inline|unsafe-eval|\*/);
      expect(pageNonces.scripts.length).toBeGreaterThan(0);
      expect(pageNonces.scripts.every((n) => n === nonce)).toBe(true);
      expect(pageNonces.marker).toBe(nonce);
      expect(secondNonce).not.toBe('');
      expect(secondNonce).not.toBe(nonce);
      expect(cspViolations).toEqual([]);
    });
});

test(BOOTSTRAP_TITLE, async ({ page }) => {
  await acceptance('US1: Establish the secure login application shell', BOOTSTRAP_TITLE, 'Anonymous browser');
  await allure.description(AC_DESCRIPTION);
  await allure.attachment('Test plan', [
    'Data:',
    '- The real /login document served to this browser, altered in the browser only: (a) every nonce reset to the build placeholder __CSP_NONCE__ with a matching policy, (b) the csp-nonce marker removed. Discovery: both leave the app root empty with no controls.',
    'Isolation:',
    '- Isolated E2E stack: harness lifecycle (e2e/scripts/lifecycle.mjs) starts the backend with the local profile on a fresh in-memory H2 database; the document alteration is a browser-side route on this page only.',
    'Steps:',
    '1. Open /login served with an unreplaced nonce.',
    '2. Observe that the login form does not appear.',
    '3. Open /login served without the nonce marker.',
    '4. Observe that the login form does not appear.',
    'Visible outcomes:',
    '- a document with a missing or unreplaced nonce does not render the login form',
    'Acceptance mapping:',
    '- AC7 | Story clause: Missing or unreplaced CSP nonces fail frontend bootstrap safely | Actor: Anonymous browser | Visible outcome: a document with a missing or unreplaced nonce does not render the login form',
    'Assumptions:',
    '- The real server always replaces the nonce, so the failure branch is reached by altering the served document in the browser; "safely" means bootstrap stops with no partial form rendered.',
  ].join('\n'), 'text/plain');

  const original = await page.goto('/login');
  const html = (await original?.text()) ?? '';
  const originalHeaders = original?.headers() ?? {};
  const nonce = nonceOf(originalHeaders['content-security-policy'] ?? '');
  expect(nonce).not.toBe('');
  const headers = Object.fromEntries(Object.entries(originalHeaders).filter(([name]) => !['content-length', 'content-encoding', 'transfer-encoding'].includes(name)));
  const variants: Array<{ name: string; body: string; policy: string }> = [
    { name: 'unreplaced nonce', body: html.replaceAll(nonce, '__CSP_NONCE__'), policy: headers['content-security-policy'].replaceAll(nonce, '__CSP_NONCE__') },
    { name: 'missing nonce marker', body: html.replace(/<meta[^>]*property="csp-nonce"[^>]*>/, ''), policy: headers['content-security-policy'] },
  ];
  const isDocument = (url: URL) => url.pathname === '/login';
  for (const variant of variants) {
    await page.route(isDocument, (route) => route.fulfill({
      status: 200, headers: { ...headers, 'content-security-policy': variant.policy }, body: variant.body,
    }));
    const bootstrapError = page.waitForEvent('pageerror');
    await page.goto('/login');
    const error = await bootstrapError;
    await page.unroute(isDocument);
    const root = await page.locator('#root').innerHTML();
    await check('Assertion: [AC7] actor: Anonymous browser | a document with a missing or unreplaced nonce does not render the login form',
      `${variant.name}: bootstrap stops; no Log in heading, fields or button are rendered`,
      `variant applied=${variant.body !== html}; bootstrap error "${error.message}"; app root "${root}"`, async () => {
        expect(variant.body).not.toBe(html);
        await expect(page.getByRole('heading', { name: 'Log in' })).toHaveCount(0);
        await expect(page.getByLabel('Username')).toHaveCount(0);
        await expect(page.getByLabel('Password')).toHaveCount(0);
        await expect(page.getByRole('button')).toHaveCount(0);
        expect(root).toBe('');
      });
  }
});
