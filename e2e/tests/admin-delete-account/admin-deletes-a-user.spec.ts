import { allure } from 'allure-playwright';
import { expect, test, logIn, enterCredentials, ADMIN_USER, type Account } from '../../fixtures/session';
import { acceptance, check } from '../../fixtures/acceptance';
import type { Page } from '@playwright/test';

const STORY = 'US11: An Admin deletes another user\'s Account';

/**
 * The table row of `username` on the User list, paging forward until it shows: the list is newest first, so a
 * long-lived account (the bootstrapped Admin) sits on a later page once other scenarios have registered theirs.
 */
async function rowOf(page: Page, username: string) {
  const table = page.getByRole('table');
  const row = table.getByRole('row', { name: new RegExp(`^${username}\\b`) });
  await expect(table).toBeVisible();
  const next = page.getByRole('navigation', { name: 'Pagination' }).getByRole('button', { name: 'Next' });
  for (let shown = 1; !(await row.isVisible()) && (await next.isEnabled()); shown++) {
    await next.click();
    // The table's caption names the page on screen once the next page has loaded.
    await expect(table).toHaveAccessibleName(new RegExp(`^Users, page ${shown + 1} of`));
  }
  await expect(row).toBeVisible();
  return row;
}

async function openUserList(page: Page) {
  await logIn(page, ADMIN_USER);
  await page.getByRole('link', { name: 'Users' }).click();
  await expect(page).toHaveURL(/\/admin\/users/);
}

test.describe(STORY, () => {
  test('an Admin deletes a User, the row reads Deleted, and that User can no longer log in', async ({ page, account }) => {
    await acceptance(STORY, 'Deleting a User tombstones it and blocks login', 'Admin');
    await allure.attachment('Test plan', [
      'Data: a scenario-owned User (the account fixture) and the harness-bootstrapped Admin.',
      'Steps: 1. Sign in as the Admin and open Users. 2. Select Delete on the User\'s row and confirm.',
      '3. Log out and sign in as the deleted User.',
      'Visible outcomes: the row reads "Deleted" with no controls; the login fails with the generic message.',
    ].join('\n'), 'text/plain');

    await openUserList(page);
    const row = await rowOf(page, account.username);
    await row.getByRole('button', { name: `Delete ${account.username}` }).click();
    const dialog = page.getByRole('alertdialog', { name: `Delete ${account.username}?` });
    await expect(dialog).toContainText('This cannot be undone.');
    const deletion = page.waitForResponse((r) => r.request().method() === 'DELETE');
    await dialog.getByRole('button', { name: 'Delete' }).click();
    const status = (await deletion).status();

    await check('Assertion: the deleted row reads Deleted', 'DELETE 204; status pill "Deleted"; no Delete control',
      `DELETE ${status}; row "${await row.textContent()}"`, async () => {
        expect(status).toBe(204);
        await expect(row.getByText('Deleted', { exact: true })).toBeVisible();
        await expect(row.getByRole('button', { name: /^Delete/ })).toHaveCount(0);
      });

    await page.getByRole('button', { name: 'Log out' }).click();
    await expect(page).toHaveURL('/login');
    await signInFails(page, account);
  });

  test('the Admin\'s own row offers no Delete', async ({ page }) => {
    await acceptance(STORY, 'An Admin cannot delete their own Account', 'Admin');
    await openUserList(page);
    const own = await rowOf(page, ADMIN_USER.username);

    await check('Assertion: the own row has no Delete control', '"Your account" and no Delete button',
      `row "${await own.textContent()}"`, async () => {
        await expect(own.getByText('Your account')).toBeVisible();
        await expect(own.getByRole('button', { name: /^Delete/ })).toHaveCount(0);
      });
  });

  test('cancelling the confirmation sends no deletion and changes nothing', async ({ page, account }) => {
    await acceptance(STORY, 'Cancelling a deletion changes nothing', 'Admin');
    const deletions: string[] = [];
    page.on('request', (request) => { if (request.method() === 'DELETE') deletions.push(request.url()); });

    await openUserList(page);
    const row = await rowOf(page, account.username);
    await row.getByRole('button', { name: `Delete ${account.username}` }).click();
    await page.getByRole('alertdialog').getByRole('button', { name: 'Cancel' }).click();

    await check('Assertion: nothing was deleted', 'no DELETE request; dialog closed; row still Active',
      `DELETE requests ${deletions.length}`, async () => {
        await expect(page.getByRole('alertdialog')).toHaveCount(0);
        expect(deletions).toEqual([]);
        await expect(row.getByText('Active', { exact: true })).toBeVisible();
        await expect(row.getByRole('button', { name: `Delete ${account.username}` })).toBeVisible();
      });
  });
});

/** Signs in as `account` and expects the generic Authentication failure, never a hint that it was deleted. */
async function signInFails(page: Page, account: Account) {
  await enterCredentials(page, account.username, account.password);
  await page.getByRole('button', { name: 'Log in' }).click();
  await check('Assertion: the deleted User cannot log in', 'the generic "Invalid username or password"',
    `URL ${new URL(page.url()).pathname}`, async () => {
      await expect(page.getByRole('alert')).toHaveText('Invalid username or password');
      await expect(page).toHaveURL('/login');
    });
}
