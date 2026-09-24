/**
 * Story 1 — visitor registration.
 *
 * Drives the real SPA on the isolated harness lifecycle. All account state is
 * observed through rendered surfaces: the register/landing/admin pages, the
 * in-browser register/login responses, and the dev-profile H2 console
 * (permit-all) for the stored credential. AC4 also reads the harness-owned
 * backend log file (E2E_BACKEND_LOG) that captures backend stdout for the
 * active lifecycle.
 */
import { readFileSync } from 'node:fs'

import { allure } from 'allure-playwright'
import type { Page } from '@playwright/test'

import {
  adminCredentials,
  expect,
  loginAs,
  logout,
  test,
} from '../../fixtures/auth'

const campaignSuite = process.env.E2E_CAMPAIGN_SUITE
if (!campaignSuite) throw new Error('E2E_CAMPAIGN_SUITE is required')

/** Backend origin (H2 console + API) on the harness-owned ports. */
const API_ORIGIN = process.env.E2E_API_URL ?? 'http://localhost:18080'

const STORY = [
  'Story 1 — As a visitor, I want to register an account with a username, email, and password, so that I can log in and access the protected app.',
  '',
  '- Given a visitor submits a unique username, unique email, and a password meeting the minimum strength policy (length ≥ 12), when they submit registration, then an account is created with role `USER`, `enabled = true`, and the password stored as a BCrypt hash.',
  "- Given a visitor submits a username or email that's already registered, when they submit registration, then the request is rejected with a clear validation error (username/email conflict), and no account is created.",
  '- Given a visitor submits a password that fails the strength policy, when they submit registration, then the request is rejected with a validation error and no account is created.',
  '- Given any registration attempt, when handled, then the plaintext password is never logged or stored.',
].join('\n');

const AC_DESCRIPTION =
  'AC1: Given a visitor submits a unique username, unique email, and a password meeting the minimum strength policy (length ≥ 12), when they submit registration, then an account is created with role `USER`, `enabled = true`, and the password stored as a BCrypt hash.'
const AC2_DESCRIPTION =
  "AC2: Given a visitor submits a username or email that's already registered, when they submit registration, then the request is rejected with a clear validation error (username/email conflict), and no account is created."
const AC3_DESCRIPTION =
  'AC3: Given a visitor submits a password that fails the strength policy, when they submit registration, then the request is rejected with a validation error and no account is created.'
const AC4_DESCRIPTION =
  'AC4: Given any registration attempt, when handled, then the plaintext password is never logged or stored.'

function isolationTag(): string {
  return `isolated E2E lifecycle (in-memory H2, create-drop); isolation_id=${process.env.E2E_ISOLATION_ID ?? 'shared'}`
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

/** Fill the register form and submit it through the real UI. */
async function submitRegistration(
  page: Page,
  username: string,
  email: string,
  password: string,
): Promise<void> {
  await page.locator('#username').fill(username)
  await page.locator('#email').fill(email)
  await page.locator('#password').fill(password)
  await page.getByRole('button', { name: 'Create account' }).click()
}

/**
 * Read one USERS row through the dev-profile H2 console (permit-all,
 * CSRF-exempt). Two textareas share name=sql (editor + submit field), so the
 * statement is set on both — same mechanics the console's own Run button uses.
 */
async function readUsersRow(page: Page, username: string): Promise<string> {
  await page.goto(`${API_ORIGIN}/h2-console`, { waitUntil: 'domcontentloaded' })
  await page.locator('input[name="url"]').fill('jdbc:h2:mem:helloauth')
  await page.locator('input[name="user"]').fill('sa')
  await page.locator('input[name="password"]').fill('')
  await page.locator('input[type="submit"][value="Connect"]').click()

  await expect
    .poll(() => page.frames().some((frame) => frame.name() === 'h2query'), {
      timeout: 15_000,
    })
    .toBe(true)
  const queryFrame = page.frames().find((frame) => frame.name() === 'h2query')
  if (!queryFrame) throw new Error('H2 console query frame did not load')
  const sql = `SELECT * FROM USERS WHERE USERNAME='${username}'`
  await queryFrame.evaluate((statement) => {
    for (const area of document.querySelectorAll('textarea[name=sql]')) {
      ;(area as HTMLTextAreaElement).value = statement
    }
  }, sql)
  await queryFrame.locator('input[type="button"][value="Run"]').click()

  const readResult = async (): Promise<string> => {
    const resultFrame = page.frames().find((frame) => frame.name() === 'h2result')
    if (!resultFrame) return ''
    return resultFrame
      .evaluate(() => document.body?.innerText ?? '')
      .catch(() => '')
  }
  await expect
    .poll(readResult, { timeout: 15_000 })
    .toContain('PASSWORD_HASH')
  return (await readResult()).trim().replace(/\s+/g, ' ')
}

test.describe('Story 1 — visitor registration', () => {
  test('AC1: registering a unique visitor creates an enabled USER account and reaches the protected app', async ({
    page,
  }) => {
    await allure.parentSuite(campaignSuite)
    await allure.feature('Feature: Registration')
    await allure.suite('US1: visitor registers an account')
    await allure.subSuite(AC_DESCRIPTION)
    await allure.description(AC_DESCRIPTION)
    await allure.parameter('persona', 'visitor')
    await allure.parameter('e2e_configuration', isolationTag())
    await allure.attachment(
      'Test plan',
      [
        'Data:',
        '- Fresh visitor identity e2e-ac1-<timestamp> with a unique email; the submitted password is exactly 12 characters, the story minimum-length boundary. Permitted oracles: the live Register page, the protected landing page, the in-browser POST /api/auth/register response, the admin users list at /admin (read-only check via the seeded admin persona), and the dev-profile H2 console for the stored credential.',
        'Isolation:',
        '- Isolated E2E stack: one harness lifecycle with in-memory H2 (create-drop); the scenario owns its fresh unique identity; anonymous register calls in this campaign stay well under the IP throttle (20 per 10 minutes).',
        'Steps:',
        '1. Open the register page and submit a unique username, unique email, and a 12-character password.',
        '2. Read the protected landing page the app navigates to.',
        '3. Sign out, sign back in with the new credentials, and read the greeting again.',
        '4. Sign in as the seeded admin and read the new account row in the users list.',
        '5. Query the USERS table through the dev H2 console and read the stored password value.',
        'Visible outcomes:',
        '- the submitted registration creates the account and the visitor reaches the protected app',
        '- the new credentials log in and the protected greeting is shown',
        '- the created account has role USER',
        '- the created account is enabled',
        '- the stored password is a BCrypt hash, not plaintext',
        'Acceptance mapping:',
        '- AC1 | Story clause: then an account is created | Actor: visitor | Visible outcome: the submitted registration creates the account and the visitor reaches the protected app',
        '- AC1 | Story clause: so that I can log in and access the protected app | Actor: visitor | Visible outcome: the new credentials log in and the protected greeting is shown',
        '- AC1 | Story clause: with role `USER` | Actor: visitor | Visible outcome: the created account has role USER',
        '- AC1 | Story clause: `enabled = true` | Actor: visitor | Visible outcome: the created account is enabled',
        '- AC1 | Story clause: the password stored as a BCrypt hash | Actor: visitor | Visible outcome: the stored password is a BCrypt hash, not plaintext',
        'Boundary coverage:',
        '- AC1 | Condition: >= 12 | Values: 12',
      ].join('\n'),
      'text/plain',
    )

    const run = Date.now().toString(36)
    const username = `e2e-ac1-${run}`
    const email = `${username}@example.com`
    const password = 'Valid-pass12' // exactly 12 characters — the story boundary value

    await page.goto('/register', { waitUntil: 'domcontentloaded' })
    await expect(page.locator('#username')).toBeVisible()
    await page.waitForFunction(() => document.cookie.includes('XSRF-TOKEN='))

    const [registerResponse] = await Promise.all([
      page.waitForResponse(
        (res) =>
          res.url().includes('/api/auth/register') &&
          res.request().method() === 'POST',
      ),
      submitRegistration(page, username, email, password),
    ])
    expect(registerResponse.status()).toBe(201)

    const greeting = page.getByRole('status')
    await greeting.scrollIntoViewIfNeeded()
    const landingUrl = page.url()
    const greetingText = (await greeting.innerText()).trim()
    await assertOutcome(
      '[AC1] visitor lands on the protected app greeted by name after registering',
      `landing on '/' showing 'Hello, ${username}' after a 12-character password registration`,
      `url ${landingUrl}; greeting '${greetingText}'; password length ${password.length}`,
      async () => {
        await expect(page).toHaveURL(/\/$/)
        await expect(greeting).toHaveText(`Hello, ${username}`)
      },
    )

    const signedIn = page.getByText(`Signed in as ${username} (USER)`)
    await signedIn.scrollIntoViewIfNeeded()
    const signedInText = (await signedIn.innerText()).trim()
    await assertOutcome(
      '[AC1] the created account signs in as a USER principal',
      `identity line 'Signed in as ${username} (USER)'`,
      `rendered '${signedInText}'`,
      async () => {
        await expect(signedIn).toBeVisible()
      },
    )

    // The new credentials must work in a fresh sign-in, not only the
    // auto-sign-in the SPA performs after registration.
    await logout(page)
    await loginAs(page, username, password)
    const greetingAfterLogin = page.getByRole('status')
    await greetingAfterLogin.scrollIntoViewIfNeeded()
    const reloginText = (await greetingAfterLogin.innerText()).trim()
    await assertOutcome(
      '[AC1] the new credentials log in and show the protected greeting',
      `sign-in with the registered credentials shows 'Hello, ${username}'`,
      `greeting '${reloginText}'`,
      async () => {
        await expect(greetingAfterLogin).toHaveText(`Hello, ${username}`)
      },
    )

    await logout(page)
    await loginAs(page, adminCredentials.username, adminCredentials.password)
    await page.goto('/admin', { waitUntil: 'domcontentloaded' })
    const accountRow = page.locator('tbody tr', { hasText: username })
    await accountRow.scrollIntoViewIfNeeded()
    const rowText = (await accountRow.innerText()).trim().replace(/\s+/g, ' ')
    await assertOutcome(
      '[AC1] the admin users list shows the new account with role USER',
      `role badge 'USER' in the row for ${username}`,
      `row '${rowText}'`,
      async () => {
        await expect(
          accountRow.getByText('USER', { exact: true }),
        ).toBeVisible()
      },
    )
    await assertOutcome(
      '[AC1] the admin users list shows the new account enabled',
      `status 'Enabled' in the row for ${username}`,
      `row '${rowText}'`,
      async () => {
        await expect(
          accountRow.getByText('Enabled', { exact: true }),
        ).toBeVisible()
      },
    )

    const storedRow = await readUsersRow(page, username)
    await assertOutcome(
      '[AC1] the stored password is a BCrypt hash, not plaintext',
      `USERS row for ${username} holding a $2a$/$2b$ BCrypt password_hash; the submitted plaintext never stored`,
      `stored row '${storedRow.length > 300 ? `${storedRow.slice(0, 300)}...` : storedRow}'`,
      async () => {
        expect(storedRow).toMatch(/\$2[aby]\$\d{2}\$/)
        expect(storedRow).not.toContain(password)
      },
    )
  })

  test('AC2: a duplicate username or email is rejected and no account is created', async ({
    page,
  }) => {
    await allure.parentSuite(campaignSuite)
    await allure.feature('Feature: Registration')
    await allure.suite('US1: visitor registers an account')
    await allure.subSuite(AC2_DESCRIPTION)
    await allure.description(AC2_DESCRIPTION)
    await allure.parameter('persona', 'visitor')
    await allure.parameter('e2e_configuration', isolationTag())
    await allure.attachment(
      'Test plan',
      [
        'Data:',
        '- The seeded dev admin identity (username admin, email admin@localhost) is the already-registered conflict. Each attempt supplies fresh unique values for the non-conflicting fields so a wrongly-created account would be detectable in the admin users list.',
        'Isolation:',
        '- Isolated E2E stack: one harness lifecycle with in-memory H2 (create-drop); rejections create no rows; the admin users list is read-only confirmation.',
        'Steps:',
        '1. Open the register page and submit the seeded admin username with a fresh unique email and a valid password.',
        '2. Read the validation error shown on the page.',
        '3. Submit again with a fresh unique username and the seeded admin email.',
        '4. Read the validation error, then check the admin users list for any account carrying either fresh value.',
        'Visible outcomes:',
        '- a duplicate username is rejected with a username-conflict validation error',
        '- a duplicate email is rejected with an email-conflict validation error',
        '- no account is created by a conflicting registration',
        'Acceptance mapping:',
        "- AC2 | Story clause: a username or email that's already registered | Actor: visitor | Visible outcome: a duplicate username is rejected with a username-conflict validation error",
        '- AC2 | Story clause: the request is rejected with a clear validation error (username/email conflict) | Actor: visitor | Visible outcome: a duplicate email is rejected with an email-conflict validation error',
        '- AC2 | Story clause: no account is created | Actor: visitor | Visible outcome: no account is created by a conflicting registration',
        'Boundary coverage:',
        '- AC2 | Condition: >= 12 | Values: 12',
      ].join('\n'),
      'text/plain',
    )

    const run = Date.now().toString(36)
    const uniqueEmail = `e2e-ac2-${run}@example.com`
    const uniqueUsername = `e2e-ac2-${run}`
    const password = 'Valid-pass12'

    await page.goto('/register', { waitUntil: 'domcontentloaded' })
    await expect(page.locator('#username')).toBeVisible()
    await page.waitForFunction(() => document.cookie.includes('XSRF-TOKEN='))
    const alert = page.getByRole('alert')

    const [usernameConflict] = await Promise.all([
      page.waitForResponse(
        (res) =>
          res.url().includes('/api/auth/register') &&
          res.request().method() === 'POST',
      ),
      submitRegistration(page, 'admin', uniqueEmail, password),
    ])
    expect(usernameConflict.status()).toBe(409)
    await expect(alert).toBeVisible()
    await alert.scrollIntoViewIfNeeded()
    const usernameAlertText = (await alert.innerText()).trim()
    await assertOutcome(
      '[AC2] a duplicate username is rejected with a username-conflict validation error',
      "alert \"Username 'admin' is already taken.\" on /register",
      `status ${usernameConflict.status()}; alert '${usernameAlertText}'; url ${page.url()}; submitted password length ${password.length}`,
      async () => {
        await expect(alert).toHaveText("Username 'admin' is already taken.")
        await expect(page).toHaveURL(/\/register$/)
      },
    )

    const [emailConflict] = await Promise.all([
      page.waitForResponse(
        (res) =>
          res.url().includes('/api/auth/register') &&
          res.request().method() === 'POST',
      ),
      submitRegistration(page, uniqueUsername, 'admin@localhost', password),
    ])
    expect(emailConflict.status()).toBe(409)
    await expect(alert).toBeVisible()
    await alert.scrollIntoViewIfNeeded()
    const emailAlertText = (await alert.innerText()).trim()
    await assertOutcome(
      '[AC2] a duplicate email is rejected with an email-conflict validation error',
      "alert \"Email 'admin@localhost' is already registered.\" on /register",
      `status ${emailConflict.status()}; alert '${emailAlertText}'; url ${page.url()}`,
      async () => {
        await expect(alert).toHaveText(
          "Email 'admin@localhost' is already registered.",
        )
        await expect(page).toHaveURL(/\/register$/)
      },
    )

    // The rejections must not have persisted either fresh identity.
    await loginAs(page, adminCredentials.username, adminCredentials.password)
    await page.goto('/admin', { waitUntil: 'domcontentloaded' })
    const usersTable = page.locator('tbody')
    await expect(usersTable).toContainText('admin@localhost')
    const tableText = (await usersTable.innerText())
      .trim()
      .replace(/\s+/g, ' ')
    await assertOutcome(
      '[AC2] no account is created by a conflicting registration',
      `admin users list with no row for ${uniqueEmail} or ${uniqueUsername}`,
      `users table '${tableText.length > 300 ? `${tableText.slice(0, 300)}...` : tableText}'`,
      async () => {
        await expect(usersTable).not.toContainText(uniqueEmail)
        await expect(usersTable).not.toContainText(uniqueUsername)
      },
    )
  })

  test('AC3: a sub-policy password is rejected and no account is created', async ({
    page,
  }) => {
    await allure.parentSuite(campaignSuite)
    await allure.feature('Feature: Registration')
    await allure.suite('US1: visitor registers an account')
    await allure.subSuite(AC3_DESCRIPTION)
    await allure.description(AC3_DESCRIPTION)
    await allure.parameter('persona', 'visitor')
    await allure.parameter('e2e_configuration', isolationTag())
    await allure.attachment(
      'Test plan',
      [
        'Data:',
        '- Fresh visitor identity e2e-ac3-<timestamp> with an 11-character password, one under the 12-character policy. The UI gate is the native minLength validation; the server-side rejection is reached by removing that client attribute and submitting the same form — no request can leave the browser while the native gate holds.',
        'Isolation:',
        '- Isolated E2E stack: one harness lifecycle with in-memory H2 (create-drop); the rejection creates no row; the admin users list is read-only confirmation.',
        'Steps:',
        '1. Open the register page, enter a unique username, unique email, and an 11-character password, then submit.',
        '2. Read the browser validation message and confirm no registration request left the page.',
        '3. Remove the client-side length attribute, submit the same data, and read the server validation error.',
        '4. Check the admin users list for any account carrying the fresh username.',
        'Visible outcomes:',
        '- a sub-policy password is rejected with a validation error',
        '- no account is created for a sub-policy password',
        'Acceptance mapping:',
        '- AC3 | Story clause: a password that fails the strength policy | Actor: visitor | Visible outcome: a sub-policy password is rejected with a validation error',
        '- AC3 | Story clause: no account is created | Actor: visitor | Visible outcome: no account is created for a sub-policy password',
        'Boundary coverage:',
        '- AC3 | Condition: >= 12 | Values: 12',
      ].join('\n'),
      'text/plain',
    )

    const run = Date.now().toString(36)
    const username = `e2e-ac3-${run}`
    const email = `${username}@example.com`
    const shortPassword = 'shortpass11' // 11 characters — one under the policy

    await page.goto('/register', { waitUntil: 'domcontentloaded' })
    await expect(page.locator('#username')).toBeVisible()
    await page.waitForFunction(() => document.cookie.includes('XSRF-TOKEN='))

    let registerRequests = 0
    page.on('response', (res) => {
      if (
        res.url().includes('/api/auth/register') &&
        res.request().method() === 'POST'
      ) {
        registerRequests += 1
      }
    })

    await submitRegistration(page, username, email, shortPassword)
    const passwordInput = page.locator('#password')
    const validationMessage = await passwordInput.evaluate(
      (el) => (el as HTMLInputElement).validationMessage,
    )
    await assertOutcome(
      '[AC3] the form gates a sub-policy password before any request leaves the browser',
      'a native validation message and zero register requests',
      `validationMessage '${validationMessage}'; register requests sent ${registerRequests}; url ${page.url()}`,
      async () => {
        expect(validationMessage.length).toBeGreaterThan(0)
        expect(registerRequests).toBe(0)
        await expect(page).toHaveURL(/\/register$/)
      },
    )

    // Server-side surface: drop the client-side minLength so the same submit
    // reaches the API, which must reject it with the policy error.
    await passwordInput.evaluate((el) =>
      (el as HTMLInputElement).removeAttribute('minlength'),
    )
    const [rejection] = await Promise.all([
      page.waitForResponse(
        (res) =>
          res.url().includes('/api/auth/register') &&
          res.request().method() === 'POST',
      ),
      page.getByRole('button', { name: 'Create account' }).click(),
    ])
    expect(rejection.status()).toBe(400)
    const alert = page.getByRole('alert')
    await expect(alert).toBeVisible()
    await alert.scrollIntoViewIfNeeded()
    const alertText = (await alert.innerText()).trim()
    await assertOutcome(
      '[AC3] a sub-policy password is rejected with a validation error',
      "alert 'Password must be at least 12 characters.' on /register",
      `status ${rejection.status()}; alert '${alertText}'; url ${page.url()}`,
      async () => {
        await expect(alert).toHaveText(
          'Password must be at least 12 characters.',
        )
        await expect(page).toHaveURL(/\/register$/)
      },
    )

    await loginAs(page, adminCredentials.username, adminCredentials.password)
    await page.goto('/admin', { waitUntil: 'domcontentloaded' })
    const usersTable = page.locator('tbody')
    await expect(usersTable).toContainText('admin@localhost')
    const tableText = (await usersTable.innerText())
      .trim()
      .replace(/\s+/g, ' ')
    await assertOutcome(
      '[AC3] no account is created for a sub-policy password',
      `admin users list with no row for ${username}`,
      `users table '${tableText.length > 300 ? `${tableText.slice(0, 300)}...` : tableText}'`,
      async () => {
        await expect(usersTable).not.toContainText(username)
        await expect(usersTable).not.toContainText(email)
      },
    )
  })

  test('AC4: the plaintext password is never logged or stored', async ({
    page,
  }) => {
    await allure.parentSuite(campaignSuite)
    await allure.feature('Feature: Registration')
    await allure.suite('US1: visitor registers an account')
    await allure.subSuite(AC4_DESCRIPTION)
    await allure.description(AC4_DESCRIPTION)
    await allure.parameter('persona', 'visitor')
    await allure.parameter('e2e_configuration', isolationTag())
    await allure.attachment(
      'Test plan',
      [
        'Data:',
        '- Fresh visitor identity e2e-ac4-<timestamp> whose plaintext password is a distinctive sentinel string. Permitted oracles: the dev-profile H2 console for the stored account record, and the harness-owned backend log file (E2E_BACKEND_LOG, the active lifecycle up log that captures backend stdout including audit lines).',
        'Isolation:',
        '- Isolated E2E stack: one harness lifecycle with in-memory H2 (create-drop); the sentinel plaintext belongs to this scenario alone and is never reused.',
        'Steps:',
        '1. Register a fresh visitor through the register page with the sentinel password.',
        '2. Query the account row through the dev H2 console and read every stored column.',
        '3. Read the captured backend log until the account login audit appears, then confirm the sentinel plaintext never does.',
        'Visible outcomes:',
        '- the plaintext password is never stored in the account record',
        '- the plaintext password is never written to application logs',
        'Acceptance mapping:',
        '- AC4 | Story clause: the plaintext password is never logged or stored | Actor: visitor | Visible outcome: the plaintext password is never stored in the account record',
        '- AC4 | Story clause: the plaintext password is never logged or stored | Actor: visitor | Visible outcome: the plaintext password is never written to application logs',
        'Boundary coverage:',
        '- AC4 | Condition: >= 12 | Values: 12',
      ].join('\n'),
      'text/plain',
    )

    const backendLogPath = process.env.E2E_BACKEND_LOG
    if (!backendLogPath) {
      throw new Error(
        'E2E_BACKEND_LOG is required: absolute path to the active lifecycle up log that captures backend stdout.',
      )
    }

    const run = Date.now().toString(36)
    const username = `e2e-ac4-${run}`
    const email = `${username}@example.com`
    const password = `${run}sentinelpw`.slice(0, 12) // unique 12-char sentinel plaintext

    await page.goto('/register', { waitUntil: 'domcontentloaded' })
    await expect(page.locator('#username')).toBeVisible()
    await page.waitForFunction(() => document.cookie.includes('XSRF-TOKEN='))
    await submitRegistration(page, username, email, password)
    await expect(
      page.getByText('Protected hello', { exact: true }),
    ).toBeVisible()

    // The auto sign-in writes a login_success audit line for this account;
    // its presence proves the captured log is live for this exact run before
    // the absence claim is read.
    const readLog = (): string => {
      try {
        return readFileSync(backendLogPath, 'utf8')
      } catch {
        return ''
      }
    }
    await expect
      .poll(() => readLog().includes(username), { timeout: 20_000 })
      .toBe(true)
    const logContent = readLog()
    const plaintextOccurrences = logContent.split(password).length - 1
    await assertOutcome(
      '[AC4] the plaintext password is never written to application logs',
      `0 occurrences of the submitted plaintext in the captured backend log; login audit for ${username} present`,
      `plaintext occurrences ${plaintextOccurrences} (password length ${password.length}); log records activity for ${username}: ${logContent.includes(username)}`,
      async () => {
        expect(plaintextOccurrences).toBe(0)
      },
    )

    const storedRow = await readUsersRow(page, username)
    await assertOutcome(
      '[AC4] the account record stores only a BCrypt hash, never the plaintext',
      `USERS row for ${username} holding a $2a$/$2b$ BCrypt password_hash; no column holds the submitted plaintext`,
      `stored row '${storedRow.length > 300 ? `${storedRow.slice(0, 300)}...` : storedRow}'`,
      async () => {
        expect(storedRow).toMatch(/\$2[aby]\$\d{2}\$/)
        expect(storedRow).not.toContain(password)
      },
    )
  })
})
