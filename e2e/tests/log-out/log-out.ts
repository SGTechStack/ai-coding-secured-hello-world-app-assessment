import type { Page } from '@playwright/test';

/** The browser's session cookie, if it holds one. */
export async function sessionCookie(page: Page) {
  return (await page.context().cookies()).find((cookie) => cookie.name === 'id');
}
