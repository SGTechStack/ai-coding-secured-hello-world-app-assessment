import { adminCredentials, expect, loginAs, logout, test } from '../fixtures/auth'

/**
 * Foundation smoke test — proves the harness can bring up the app, drive the
 * real login form, reach the protected landing page, read the personalized
 * greeting, and sign back out. It exercises the reusable auth fixture and the
 * lifecycle reset contract (each run starts from a freshly restarted backend).
 *
 * This is a harness smoke, NOT a story spec: it uses only user-visible,
 * role-based selectors and contains no fetch/page.request/child_process.
 */
test.describe('foundation smoke', () => {
  test('admin can log in, see the protected greeting, and log out', async ({
    page,
  }) => {
    await loginAs(page, adminCredentials.username, adminCredentials.password)

    // Navigation landed on the protected page (title is a CardTitle <div>).
    await expect(
      page.getByText('Protected hello', { exact: true }),
    ).toBeVisible()

    // The greeting region (role=status) resolves to the API's message, not the
    // loading or error text.
    const greeting = page.getByRole('status')
    await expect(greeting).toBeVisible()
    await expect(greeting).not.toHaveText(/Contacting \/api\/hello/)
    await expect(greeting).not.toHaveText(/Could not reach the API/)

    // Signed-in identity is shown (scoped to the "Signed in as" line so the
    // greeting's own "Hello, admin" can't double-match).
    await expect(
      page.getByText(`Signed in as ${adminCredentials.username}`),
    ).toBeVisible()

    await logout(page)

    // Back to the anonymous login form.
    await expect(page.getByRole('button', { name: 'Sign in' })).toBeVisible()
  })
})
