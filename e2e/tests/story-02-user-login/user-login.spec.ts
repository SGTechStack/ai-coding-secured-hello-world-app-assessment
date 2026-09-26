/**
 * Story 2 — registered user login.
 *
 * Drives the real SPA on the isolated harness lifecycle. Visible outcomes are
 * observed through the /login and protected landing pages, the in-browser
 * login responses, the browser cookie jar, and the dev-profile H2 console
 * (permit-all) for the private failed_login_attempts / locked_until columns
 * and the SPRING_SESSION table.
 *
 * Per-IP login throttle is 4 failures / 10 min. This spec spends 3 failed
 * credential checks in total (AC1: 1, AC2: 2); a locked-account rejection is
 * decided before credential verification and does not count.
 */
import { allure } from 'allure-playwright'
import type { BrowserContext, Page } from '@playwright/test'

import { expect, logout, test } from '../../fixtures/auth'

const campaignSuite = process.env.E2E_CAMPAIGN_SUITE
if (!campaignSuite) throw new Error('E2E_CAMPAIGN_SUITE is required')

/** Backend origin (H2 console + API) on the harness-owned ports. */
const API_ORIGIN = process.env.E2E_API_URL ?? 'http://localhost:18080'

const GENERIC_LOGIN_ERROR = 'Invalid username or password.'

const STORY = [
  'Story 2 — As a registered user, I want to log in with my username and password, so that I can access my session and the protected app content.',
  '',
  '- Given a registered, enabled, non-locked account with correct credentials, when the user submits login, then a server-side session is created, a secure session cookie is set, and `failed_login_attempts` resets to 0.',
  '- Given incorrect credentials, when the user submits login, then the request is rejected with a generic error message that does not reveal whether the username exists, and `failed_login_attempts` increments.',
  '- Given an account that is currently locked (`locked_until` in the future), when the user submits login with correct credentials, then the request is still rejected until the lockout expires.',
].join('\n');
const AC_DESCRIPTION =
  'AC1: Given a registered, enabled, non-locked account with correct credentials, when the user submits login, then a server-side session is created, a secure session cookie is set, and `failed_login_attempts` resets to 0.'
const AC2_DESCRIPTION =
  'AC2: Given incorrect credentials, when the user submits login, then the request is rejected with a generic error message that does not reveal whether the username exists, and `failed_login_attempts` increments.'
const AC3_DESCRIPTION =
  'AC3: Given an account that is currently locked (`locked_until` in the future), when the user submits login with correct credentials, then the request is still rejected until the lockout expires.'

function isolationTag(): string {
  return `isolated E2E lifecycle (in-memory H2, create-drop); isolation_id=${process.env.E2E_ISOLATION_ID ?? 'shared'}`
}

/** Shared hierarchy labels; each test sets its own AC subSuite/description. */
async function labelScenario(): Promise<void> {
  await allure.parentSuite(campaignSuite!)
  await allure.feature('Feature: Login')
  await allure.suite('US2: registered user logs in')
  await allure.parameter('persona', 'registered user')
  await allure.parameter('e2e_configuration', isolationTag())
}

/**
 * One named, reviewable outcome: the Assertion step carries the plain-language
 * claim, the nested Evidence step pins the runtime expected/observed pair.
 */
async function assertOutcome(
  assertionTitle: string,
  expected: string,
  observed: string,
  check: () => Promise<void>,
): Promise<void> {
  await allure.step(`Assertion: ${assertionTitle}`, async () => {
    await allure.step(`Evidence: expected: ${expected} | observed: ${observed}`, check)
  })
}

/**
 * Registers a fresh account through the real /register page (the SPA signs
 * it in automatically), then signs out so the scenario starts anonymous.
 */
async function registerFreshAccount(page: Page, username: string, password: string): Promise<void> {
  await allure.step(`Setup: register fresh account ${username} and sign out`, async () => {
    const csrfReady = page.waitForResponse(
      (res) => res.url().includes('/api/auth/csrf') && res.ok(),
    )
    await page.goto('/register', { waitUntil: 'domcontentloaded' })
    await csrfReady
    await page.locator('#username').fill(username)
    await page.locator('#email').fill(`${username}@example.com`)
    await page.locator('#password').fill(password)
    const [response] = await Promise.all([
      page.waitForResponse(
        (res) => res.url().includes('/api/auth/register') && res.request().method() === 'POST',
      ),
      page.getByRole('button', { name: 'Create account' }).click(),
    ])
    expect(response.status()).toBe(201)
    await expect(page.getByRole('status')).toHaveText(`Hello, ${username}`)
    await logout(page)
  })
}

/** Submit the real /login form and return the POST /api/auth/login status. */
async function submitLogin(page: Page, username: string, password: string): Promise<number> {
  if (!page.url().endsWith('/login')) {
    await page.goto('/login', { waitUntil: 'domcontentloaded' })
  }
  await page.locator('#username').fill(username)
  await page.locator('#password').fill(password)
  const [response] = await Promise.all([
    page.waitForResponse(
      (res) => res.url().includes('/api/auth/login') && res.request().method() === 'POST',
    ),
    page.getByRole('button', { name: 'Sign in' }).click(),
  ])
  return response.status()
}

/**
 * Run one statement through the dev-profile H2 console (permit-all,
 * CSRF-exempt) in a separate tab, so the SPA page keeps its in-memory state.
 * Two textareas share name=sql (editor + submit field), so the statement is
 * set on both — the same mechanics the console's own Run button uses.
 */
async function runH2(context: BrowserContext, sql: string, expectText: string): Promise<string> {
  const console = await context.newPage()
  try {
    await console.goto(`${API_ORIGIN}/h2-console`, { waitUntil: 'domcontentloaded' })
    await console.locator('input[name="url"]').fill('jdbc:h2:mem:helloauth')
    await console.locator('input[name="user"]').fill('sa')
    await console.locator('input[name="password"]').fill('')
    await console.locator('input[type="submit"][value="Connect"]').click()
    await expect
      .poll(() => console.frames().some((frame) => frame.name() === 'h2query'), { timeout: 15_000 })
      .toBe(true)
    const queryFrame = console.frames().find((frame) => frame.name() === 'h2query')
    if (!queryFrame) throw new Error('H2 console query frame did not load')
    await queryFrame.evaluate((statement) => {
      for (const area of document.querySelectorAll('textarea[name=sql]')) {
        ;(area as HTMLTextAreaElement).value = statement
      }
    }, sql)
    await queryFrame.locator('input[type="button"][value="Run"]').click()
    const readResult = async (): Promise<string> => {
      const resultFrame = console.frames().find((frame) => frame.name() === 'h2result')
      if (!resultFrame) return ''
      return resultFrame.evaluate(() => document.body?.innerText ?? '').catch(() => '')
    }
    await expect.poll(readResult, { timeout: 15_000 }).toContain(expectText)
    return (await readResult()).trim().replace(/\s+/g, ' ')
  } finally {
    await console.close()
  }
}

/** FAILED_LOGIN_ATTEMPTS for one account, read through the H2 console. */
async function failedAttempts(context: BrowserContext, username: string): Promise<number> {
  const result = await runH2(
    context,
    `SELECT FAILED_LOGIN_ATTEMPTS AS FLA FROM USERS WHERE USERNAME='${username}'`,
    'FLA',
  )
  const match = result.match(/FLA (\d+)/)
  if (!match) throw new Error(`Could not read FAILED_LOGIN_ATTEMPTS from: ${result}`)
  return Number(match[1])
}

test.describe('Story 2 — registered user login', () => {
  test('AC1: correct credentials create a server-side session with a protected session cookie and reset failed_login_attempts', async ({
    page,
    context,
  }) => {
    await labelScenario()
    await allure.subSuite(AC_DESCRIPTION)
    await allure.description(AC_DESCRIPTION)
    await allure.attachment(
      'Test plan',
      [
        'Data:',
        '- Fresh registered, enabled, non-locked account e2e-s2ac1-<timestamp> with a 12-character password. Permitted oracles: the live /login and protected landing pages, the in-browser POST /api/auth/login response, the browser cookie jar, and the dev-profile H2 console (USERS.FAILED_LOGIN_ATTEMPTS, SPRING_SESSION.PRINCIPAL_NAME).',
        '- Material assumption: the Secure cookie attribute is set only by the prod profile; the dev stack runs over HTTP (a documented, accepted PRD gap), so HttpOnly and SameSite are asserted live and Secure is recorded as absent in dev.',
        'Isolation:',
        '- Isolated E2E stack: one harness lifecycle with in-memory H2 (create-drop); the scenario owns its fresh identity; 1 failed credential check from this IP (throttle is 4 per 10 minutes).',
        'Steps:',
        '1. Register a fresh account through the UI and sign out.',
        '2. Submit one wrong password so the account has a failure recorded, and read FAILED_LOGIN_ATTEMPTS.',
        '3. Submit the correct credentials on /login and read the protected landing page.',
        '4. Read the browser cookies and the SPRING_SESSION row for the user.',
        '5. Read FAILED_LOGIN_ATTEMPTS again.',
        'Visible outcomes:',
        '- correct-credential login creates a session and the user reaches the protected app',
        '- login sets a session cookie with the protective attributes observable in dev (HttpOnly, SameSite)',
        "- a successful login resets the account's failed_login_attempts to 0",
        'Acceptance mapping:',
        '- AC1 | Story clause: then a server-side session is created | Actor: registered user | Visible outcome: correct-credential login creates a session and the user reaches the protected app',
        '- AC1 | Story clause: a secure session cookie is set | Actor: registered user | Visible outcome: login sets a session cookie with the protective attributes observable in dev (HttpOnly, SameSite)',
        "- AC1 | Story clause: `failed_login_attempts` resets to 0 | Actor: registered user | Visible outcome: a successful login resets the account's failed_login_attempts to 0",
      ].join('\n'),
      'text/plain',
    )

    const username = `e2e-s2ac1-${Date.now().toString(36)}`
    const password = 'Valid-pass12'
    await registerFreshAccount(page, username, password)

    const failedStatus = await submitLogin(page, username, 'wrong-password-1')
    expect(failedStatus).toBe(401)
    const before = await failedAttempts(context, username)
    expect(before).toBe(1)

    const status = await submitLogin(page, username, password)
    const greeting = page.getByRole('status')
    await expect(greeting).toHaveText(`Hello, ${username}`)
    const greetingText = (await greeting.innerText()).trim()
    const sessionRow = await runH2(
      context,
      `SELECT PRINCIPAL_NAME FROM SPRING_SESSION WHERE PRINCIPAL_NAME='${username}'`,
      'PRINCIPAL_NAME',
    )
    await assertOutcome(
      '[AC1] correct credentials sign the user in and a server-side session row exists',
      `POST /api/auth/login 200, landing '/' with 'Hello, ${username}', SPRING_SESSION row for ${username}`,
      `status ${status}; url ${page.url()}; greeting '${greetingText}'; H2 '${sessionRow}'`,
      async () => {
        expect(status).toBe(200)
        await expect(page).toHaveURL(/\/$/)
        await expect(page.getByText(`Signed in as ${username} (USER)`)).toBeVisible()
        expect(sessionRow).toContain(username)
      },
    )

    const cookies = await context.cookies(API_ORIGIN)
    const session = cookies.find((c) => c.name === 'SESSION')
    const names = cookies.map((c) => c.name).join(',')
    await assertOutcome(
      '[AC1] the session cookie is HttpOnly and SameSite, and no CSRF cookie is set',
      'only cookie SESSION with httpOnly=true, sameSite=Lax (Secure is prod-profile only)',
      `cookies [${names}]; SESSION httpOnly=${session?.httpOnly} sameSite=${session?.sameSite} secure=${session?.secure}`,
      async () => {
        expect(session).toBeDefined()
        expect(session!.httpOnly).toBe(true)
        expect(session!.sameSite).toBe('Lax')
        expect(cookies.filter((c) => c.name.toUpperCase().includes('XSRF'))).toEqual([])
      },
    )

    const after = await failedAttempts(context, username)
    await assertOutcome(
      "[AC1] a successful login resets the account's failed_login_attempts to 0",
      'FAILED_LOGIN_ATTEMPTS 1 before the successful login, 0 after',
      `before ${before}; after ${after}`,
      async () => {
        expect(before).toBe(1)
        expect(after).toBe(0)
      },
    )
  })

  test('AC2: incorrect credentials get the same generic error for a real and an unknown username, and failed_login_attempts increments', async ({
    page,
    context,
  }) => {
    await labelScenario()
    await allure.subSuite(AC2_DESCRIPTION)
    await allure.description(AC2_DESCRIPTION)
    await allure.attachment(
      'Test plan',
      [
        'Data:',
        '- Fresh registered account e2e-s2ac2-<timestamp>; an unknown username e2e-s2none-<timestamp> that was never registered. Permitted oracles: the /login page alert, the in-browser POST /api/auth/login responses, and the dev-profile H2 console (USERS.FAILED_LOGIN_ATTEMPTS).',
        'Isolation:',
        '- Isolated E2E stack: one harness lifecycle with in-memory H2 (create-drop); the scenario owns its fresh identity; 2 failed credential checks from this IP (campaign total 3, throttle is 4 per 10 minutes).',
        'Steps:',
        '1. Register a fresh account through the UI and sign out; read FAILED_LOGIN_ATTEMPTS.',
        '2. Submit a wrong password for the real account and read the alert.',
        '3. Submit a login for a username that does not exist and read the alert.',
        '4. Read FAILED_LOGIN_ATTEMPTS again.',
        'Visible outcomes:',
        '- a wrong-password login and an unknown-username login are both rejected with the identical generic message',
        "- a wrong-password login increments the account's failed_login_attempts",
        'Acceptance mapping:',
        '- AC2 | Story clause: the request is rejected with a generic error message that does not reveal whether the username exists | Actor: registered user | Visible outcome: a wrong-password login and an unknown-username login are both rejected with the identical generic message',
        "- AC2 | Story clause: `failed_login_attempts` increments | Actor: registered user | Visible outcome: a wrong-password login increments the account's failed_login_attempts",
      ].join('\n'),
      'text/plain',
    )

    const run = Date.now().toString(36)
    const username = `e2e-s2ac2-${run}`
    const unknown = `e2e-s2none-${run}`
    await registerFreshAccount(page, username, 'Valid-pass12')
    const before = await failedAttempts(context, username)

    const alert = page.getByRole('alert')
    const wrongStatus = await submitLogin(page, username, 'wrong-password-1')
    await expect(alert).toBeVisible()
    const wrongText = (await alert.innerText()).trim()
    const unknownStatus = await submitLogin(page, unknown, 'wrong-password-1')
    await expect(alert).toBeVisible()
    const unknownText = (await alert.innerText()).trim()
    await assertOutcome(
      '[AC2] wrong password and unknown username are rejected with the identical generic message',
      `both 401 with alert '${GENERIC_LOGIN_ERROR}', still on /login`,
      `wrong password: ${wrongStatus} '${wrongText}'; unknown username: ${unknownStatus} '${unknownText}'; url ${page.url()}`,
      async () => {
        expect(wrongStatus).toBe(401)
        expect(unknownStatus).toBe(401)
        expect(wrongText).toBe(GENERIC_LOGIN_ERROR)
        expect(unknownText).toBe(wrongText)
        await expect(page).toHaveURL(/\/login$/)
      },
    )

    const after = await failedAttempts(context, username)
    await assertOutcome(
      "[AC2] a wrong-password login increments the account's failed_login_attempts",
      `FAILED_LOGIN_ATTEMPTS ${before} before, ${before + 1} after one wrong password`,
      `before ${before}; after ${after}`,
      async () => {
        expect(before).toBe(0)
        expect(after).toBe(before + 1)
      },
    )
  })

  test('AC3: a locked account is rejected with correct credentials until the lockout expires', async ({
    page,
    context,
  }) => {
    await labelScenario()
    await allure.subSuite(AC3_DESCRIPTION)
    await allure.description(AC3_DESCRIPTION)
    await allure.attachment(
      'Test plan',
      [
        'Data:',
        '- Fresh registered account e2e-s2ac3-<timestamp> with its correct 12-character password. The locked precondition is seeded through the dev-profile H2 console (USERS.LOCKED_UNTIL = now + 1 hour) because the per-IP throttle (4) trips before the account lockout threshold (5), so lockout cannot be reached from one browser IP. Permitted oracles: the /login page alert, the in-browser POST /api/auth/login responses, the protected landing page.',
        'Isolation:',
        '- Isolated E2E stack: one harness lifecycle with in-memory H2 (create-drop); the scenario owns its fresh identity; a locked rejection is decided before credential checks and does not consume the IP failure budget.',
        'Steps:',
        '1. Register a fresh account through the UI and sign out.',
        '2. Set LOCKED_UNTIL one hour in the future through the H2 console.',
        '3. Submit the correct credentials on /login and read the alert.',
        '4. Move LOCKED_UNTIL one minute into the past (lockout expired) and submit the correct credentials again.',
        'Visible outcomes:',
        '- a locked account is rejected even with correct credentials while locked_until is in the future',
        'Acceptance mapping:',
        '- AC3 | Story clause: then the request is still rejected until the lockout expires | Actor: registered user | Visible outcome: a locked account is rejected even with correct credentials while locked_until is in the future',
      ].join('\n'),
      'text/plain',
    )

    const username = `e2e-s2ac3-${Date.now().toString(36)}`
    const password = 'Valid-pass12'
    await registerFreshAccount(page, username, password)

    const lockResult = await runH2(
      context,
      `UPDATE USERS SET LOCKED_UNTIL = DATEADD('HOUR', 1, CURRENT_TIMESTAMP) WHERE USERNAME='${username}'`,
      'Update count: 1',
    )
    const lockedStatus = await submitLogin(page, username, password)
    const alert = page.getByRole('alert')
    await expect(alert).toBeVisible()
    const lockedText = (await alert.innerText()).trim()
    await assertOutcome(
      '[AC3] correct credentials are rejected while locked_until is in the future',
      `401 with alert '${GENERIC_LOGIN_ERROR}', user stays on /login`,
      `H2 '${lockResult}'; status ${lockedStatus}; alert '${lockedText}'; url ${page.url()}`,
      async () => {
        expect(lockedStatus).toBe(401)
        expect(lockedText).toBe(GENERIC_LOGIN_ERROR)
        await expect(page).toHaveURL(/\/login$/)
        await expect(page.getByText('Protected hello', { exact: true })).toHaveCount(0)
      },
    )

    const expireResult = await runH2(
      context,
      `UPDATE USERS SET LOCKED_UNTIL = DATEADD('MINUTE', -1, CURRENT_TIMESTAMP) WHERE USERNAME='${username}'`,
      'Update count: 1',
    )
    const expiredStatus = await submitLogin(page, username, password)
    const greeting = page.getByRole('status')
    await expect(greeting).toHaveText(`Hello, ${username}`)
    const greetingText = (await greeting.innerText()).trim()
    await assertOutcome(
      '[AC3] once the lockout has expired the same correct credentials sign in',
      `200 and landing '/' with 'Hello, ${username}'`,
      `H2 '${expireResult}'; status ${expiredStatus}; greeting '${greetingText}'; url ${page.url()}`,
      async () => {
        expect(expiredStatus).toBe(200)
        await expect(page).toHaveURL(/\/$/)
      },
    )
  })
})
