import { expect, test, logIn } from '../../fixtures/session';
import { sessionCookie } from './log-out';

test('AC2: a session cookie captured before Logout is rejected as unauthenticated when replayed', async ({ page, account, playwright }) => {
  await logIn(page, account);
  const captured = await sessionCookie(page);
  expect(captured).toBeDefined();

  // A separate client with no cookie jar of its own, replaying only the captured cookie.
  const attacker = await playwright.request.newContext({ baseURL: test.info().project.use.baseURL, ignoreHTTPSErrors: true });
  const replay = (path: string, accept: string) =>
    attacker.get(path, { headers: { Cookie: `id=${captured?.value}`, Accept: accept }, maxRedirects: 0 });
  try {
    expect((await replay('/api/profile', 'application/json')).status(), 'the cookie is live before Logout').toBe(200);

    await page.getByRole('button', { name: 'Log out' }).click();
    await expect(page).toHaveURL('/login');

    const api = await replay('/api/anything', 'application/json');
    expect(api.status()).toBe(401);
    expect((await api.json() as { code: string }).code).toBe('AUTHENTICATION_REQUIRED');
    // Pages hold no data (ADR 0007): the Session restore probe is what the replayed cookie no longer opens.
    const profile = await replay('/api/profile', 'application/json');
    expect(profile.status()).toBe(401);
    expect((await profile.json() as { code: string }).code).toBe('AUTHENTICATION_REQUIRED');
  } finally {
    await attacker.dispose();
  }
});
