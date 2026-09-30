import { allure } from 'allure-playwright';
import type { Page } from '@playwright/test';
import { expect, test } from '../../fixtures/session';
import { acceptance, check } from '../../fixtures/acceptance';

const AC_DESCRIPTION = 'AC3: The login page is keyboard-accessible and usable at mobile and desktop viewport sizes, with touch targets of at least 44×44px on mobile.';
const MOBILE_TITLE = AC_DESCRIPTION.replace(/^AC3:/, 'AC3b:');

const hasHorizontalScroll = (page: Page) => page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth);

test(AC_DESCRIPTION, async ({ page }) => {
  await acceptance('US1: Establish the secure login application shell', AC_DESCRIPTION, 'Keyboard-only visitor (desktop 1280x800)');
  await allure.description(AC_DESCRIPTION);
  await allure.attachment('Test plan', [
    'Data:',
    '- Unregistered username e2e-keyboard-visitor with a wrong password (no account needed). Discovery: Tab from load focuses Username, Password, Log in; Enter submits; at 1280x800 scrollWidth equals the viewport width.',
    'Isolation:',
    '- Isolated E2E stack: harness lifecycle (e2e/scripts/lifecycle.mjs) starts the backend with the local profile on a fresh in-memory H2 database; nothing shared is mutated.',
    'Steps:',
    '1. Open /login at a desktop viewport.',
    '2. Press Tab to move through Username, Password and Log in.',
    '3. Type credentials with the keyboard and press Enter.',
    '4. Check every control is on screen without horizontal scrolling.',
    'Visible outcomes:',
    '- Tab moves focus through Username, Password and the Log in button in order',
    '- all form controls are visible without horizontal scrolling at mobile and desktop viewports',
    'Acceptance mapping:',
    '- AC3 | Story clause: The login page is keyboard-accessible | Actor: Keyboard-only visitor | Visible outcome: Tab moves focus through Username, Password and the Log in button in order',
    '- AC3 | Story clause: usable at mobile and desktop viewport sizes | Actor: Keyboard-only visitor | Visible outcome: all form controls are visible without horizontal scrolling at mobile and desktop viewports',
    'Assumptions:',
    '- This scenario covers the desktop viewport and keyboard operation; the AC3b scenario covers the mobile viewport and touch targets.',
  ].join('\n'), 'text/plain');

  await page.setViewportSize({ width: 1280, height: 800 });
  await page.goto('/login');
  const username = page.getByLabel('Username');
  const password = page.getByLabel('Password');
  const button = page.getByRole('button', { name: 'Log in', exact: true });
  await expect(button).toBeVisible();
  const order: string[] = [];
  for (let i = 0; i < 3; i += 1) {
    await page.keyboard.press('Tab');
    order.push(await page.evaluate(() => {
      const el = document.activeElement as HTMLElement | null;
      return el?.id || el?.textContent?.trim() || el?.tagName || '';
    }));
  }
  await check('Assertion: [AC3] actor: Keyboard-only visitor | Tab moves focus through Username, Password and the Log in button in order',
    'username → password → Log in', order.join(' → '), async () => {
      expect(order).toEqual(['username', 'password', 'Log in']);
      await expect(button).toBeFocused();
    });

  await username.focus();
  await page.keyboard.type('e2e-keyboard-visitor');
  await page.keyboard.press('Tab');
  await page.keyboard.type('WrongPassword123!');
  await page.keyboard.press('Enter');
  const alert = page.getByRole('alert');
  // One real Login attempt (a BCrypt check); under a parallel run it can outlast the default 5s.
  await expect(alert).toBeVisible({ timeout: 15_000 });
  await alert.scrollIntoViewIfNeeded();
  await check('Assertion: [AC3] actor: Keyboard-only visitor | Tab moves focus through Username, Password and the Log in button in order',
    'pressing Enter in the form submits it and the login feedback appears', `alert "${await alert.textContent()}"`, async () => {
      await expect(alert).toHaveText('Invalid username or password');
    });

  const scrolls = await hasHorizontalScroll(page);
  await check('Assertion: [AC3] actor: Keyboard-only visitor | all form controls are visible without horizontal scrolling at mobile and desktop viewports',
    'desktop 1280x800: no horizontal scroll; Username, Password and Log in fully inside the viewport', `horizontal scroll=${scrolls}`, async () => {
      expect(scrolls).toBe(false);
      for (const control of [username, password, button]) await expect(control).toBeInViewport({ ratio: 1 });
    });
});

test.describe('mobile viewport', () => {
  test.use({ viewport: { width: 375, height: 667 }, isMobile: true, hasTouch: true });

  test(MOBILE_TITLE, async ({ page }) => {
    await acceptance('US1: Establish the secure login application shell', MOBILE_TITLE, 'Mobile visitor (375x667 touch)');
    await allure.description(AC_DESCRIPTION);
    await allure.attachment('Test plan', [
      'Data:',
      '- No account data. Discovery at 375x667 touch: scrollWidth 375; Username, Password and Log in each 295x44 CSS px.',
      'Isolation:',
      '- Isolated E2E stack: harness lifecycle (e2e/scripts/lifecycle.mjs) starts the backend with the local profile on a fresh in-memory H2 database; nothing shared is mutated.',
      'Steps:',
      '1. Open /login on a 375x667 touch phone viewport.',
      '2. Check every form control is on screen without horizontal scrolling.',
      '3. Measure the Username, Password and Log in touch targets.',
      'Visible outcomes:',
      '- all form controls are visible without horizontal scrolling at mobile and desktop viewports',
      '- Username, Password and Log in controls each measure at least 44x44 CSS px on mobile',
      'Acceptance mapping:',
      '- AC3 | Story clause: usable at mobile and desktop viewport sizes | Actor: Mobile visitor | Visible outcome: all form controls are visible without horizontal scrolling at mobile and desktop viewports',
      '- AC3 | Story clause: touch targets of at least 44×44px on mobile | Actor: Mobile visitor | Visible outcome: Username, Password and Log in controls each measure at least 44x44 CSS px on mobile',
      'Assumptions:',
      '- 44px is an inclusive minimum on both dimensions.',
    ].join('\n'), 'text/plain');

    await page.goto('/login');
    const controls = {
      Username: page.getByLabel('Username'),
      Password: page.getByLabel('Password'),
      'Log in': page.getByRole('button', { name: 'Log in', exact: true }),
    };
    await expect(controls['Log in']).toBeVisible();
    const scrolls = await hasHorizontalScroll(page);
    await check('Assertion: [AC3] actor: Mobile visitor | all form controls are visible without horizontal scrolling at mobile and desktop viewports',
      'mobile 375x667: no horizontal scroll; Username, Password and Log in fully inside the viewport', `horizontal scroll=${scrolls}`, async () => {
        expect(scrolls).toBe(false);
        for (const control of Object.values(controls)) await expect(control).toBeInViewport({ ratio: 1 });
      });
    const sizes: Record<string, { width: number; height: number }> = {};
    for (const [name, control] of Object.entries(controls)) {
      const box = await control.boundingBox();
      sizes[name] = { width: box?.width ?? 0, height: box?.height ?? 0 };
    }
    await check('Assertion: [AC3] actor: Mobile visitor | Username, Password and Log in controls each measure at least 44x44 CSS px on mobile',
      'each control at least 44x44', Object.entries(sizes).map(([n, s]) => `${n} ${s.width}x${s.height}`).join(', '), async () => {
        for (const size of Object.values(sizes)) {
          expect(size.width).toBeGreaterThanOrEqual(44);
          expect(size.height).toBeGreaterThanOrEqual(44);
        }
      });
  });
});
