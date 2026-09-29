import crypto from 'node:crypto'
import type { Browser, Page, TestInfo } from '@playwright/test'
import { allure } from 'allure-playwright'
import { expect, test, type Persona } from '../../fixtures/test'
import type { Lifecycle } from '../../fixtures/lifecycle'

const campaignSuite = process.env.E2E_CAMPAIGN_SUITE
if (!campaignSuite) throw new Error('E2E_CAMPAIGN_SUITE is required')

const STORY = '**Story 1** — As a **visitor**, I want to register an account with a username, email, and password, so that I can log in and access the protected app.\n\n- Given a visitor submits a unique username, unique email, and a password meeting the minimum strength policy (length ≥ 12), when they submit registration, then an account is created with role `USER`, `enabled = true`, and the password stored as a BCrypt hash.\n- Given a visitor submits a username or email that\'s already registered, when they submit registration, then the request is rejected with a clear validation error (username/email conflict), and no account is created.\n- Given a visitor submits a password that fails the strength policy, when they submit registration, then the request is rejected with a validation error and no account is created.\n- Given any registration attempt, when handled, then the plaintext password is never logged or stored.\n\n### Login'
const AC_DESCRIPTION = "AC2: Given a visitor submits a username or email that's already registered, when they submit registration, then the request is rejected with a clear validation error (username/email conflict), and no account is created."
const ISOLATION = [
  'Isolation:',
  '- Isolated E2E stack (the lifecycle stack with its own H2 database and backend log); no reset endpoints or private APIs.',
  '- Every username, email and password is generated uniquely for this scenario, so run order and other scenarios do not matter.',
].join('\n');

void STORY

async function scenario(label: string) {
  await allure.parentSuite(campaignSuite!)
  await allure.feature('Feature: Registration')
  await allure.suite('US1: Register an account')
  await allure.subSuite(label)
  await allure.parameter('persona', 'visitor')
  await allure.parameter('e2e_configuration', 'isolated E2E lifecycle stack')
}

async function claim(title: string, expected: string, observed: string, check: () => Promise<void>) {
  await allure.step(title, async () => allure.step(`Evidence: expected: ${expected} | observed: ${observed}`, check))
}

/** A unique ASCII password of exactly `length` characters. */
function passwordOfLength(length: number): string {
  const value = `Pw-${crypto.randomBytes(length).toString('hex')}`.slice(0, length)
  if ([...value].length !== length) throw new Error(`generated password is not ${length} characters`)
  return value
}

function visitor(passwordLength: number): Persona {
  const s = `${Date.now().toString(36)}${crypto.randomBytes(3).toString('hex')}`
  return { username: `s1_${s}`, email: `s1_${s}@example.com`, password: passwordOfLength(passwordLength) }
}

async function fillRegistration(page: Page, person: Persona) {
  await page.goto('/register')
  await expect(page.getByRole('heading', { name: 'Create an account' })).toBeVisible()
  await page.getByRole('textbox', { name: 'Username' }).fill(person.username)
  await page.getByRole('textbox', { name: 'Email' }).fill(person.email)
  await page.getByLabel('Password', { exact: true }).fill(person.password)
  await page.getByLabel('Confirm password').fill(person.password)
}

function registerResponse(page: Page) {
  return page.waitForResponse((r) => r.url().endsWith('/api/auth/register') && r.request().method() === 'POST')
}

async function signIn(page: Page, username: string, password: string) {
  await page.goto('/login')
  await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible()
  await page.getByLabel('Username').fill(username)
  await page.getByLabel('Password').fill(password)
  await page.getByRole('button', { name: 'Sign in' }).click()
}

/** Signs the ADMIN persona in within its own browser context and opens the Users list. */
async function adminUsersPage(browser: Browser, lifecycle: Lifecycle, admin: Persona, testInfo: TestInfo) {
  const context = await browser.newContext({
    baseURL: lifecycle.webUrl,
    recordVideo: { dir: testInfo.outputPath('admin-context-video') },
  })
  const page = await context.newPage()
  await signIn(page, admin.username, admin.password)
  await expect(page.getByRole('heading', { name: `Hello, ${admin.username}` })).toBeVisible()
  await page.goto('/admin')
  await expect(page.getByRole('heading', { name: 'Users' })).toBeVisible()
  await expect(page.getByRole('table', { name: 'Registered users' })).toBeVisible()
  const close = async () => {
    const video = page.video()
    await context.close()
    if (video) await testInfo.attach('admin context video', { path: await video.path(), contentType: 'video/webm' })
  }
  return { page, close }
}

/** Walks every page of the admin Users list and returns the complete set of rows it shows. */
async function allListedUsers(page: Page): Promise<{ username: string; email: string }[]> {
  const pager = page.getByRole('navigation', { name: 'Pagination' })
  const rows = page.getByRole('table', { name: 'Registered users' }).getByRole('row')
  const users: { username: string; email: string }[] = []
  for (let n = 1; ; n++) {
    await expect(pager).toContainText(`Page ${n} of `)
    const count = await rows.count()
    for (let i = 1; i < count; i++) {
      const row = rows.nth(i)
      const username = (await row.getByRole('rowheader').innerText()).replace(/\s*\(you\)$/, '').trim()
      const email = (await row.getByRole('cell').first().innerText()).trim()
      users.push({ username, email })
    }
    const next = pager.getByRole('button', { name: 'Next' })
    if (await next.isDisabled()) return users
    await next.click()
  }
}

/** Submits a conflicting registration and returns the rejected form's visible state. */
async function submitConflict(page: Page, attempt: Persona) {
  await fillRegistration(page, attempt)
  const rejected = registerResponse(page)
  await page.getByRole('button', { name: 'Create account' }).click()
  await rejected
  const alert = page.getByRole('alert')
  await alert.waitFor()
  return {
    alert,
    usernameField: page.getByRole('textbox', { name: 'Username' }),
    emailField: page.getByRole('textbox', { name: 'Email' }),
  }
}

/** Tries the rejected credentials on the Sign in page and returns the visible error. */
async function failedSignIn(page: Page, attempt: Persona) {
  await signIn(page, attempt.username, attempt.password)
  const loginError = page.getByRole('alert')
  await loginError.waitFor()
  await loginError.scrollIntoViewIfNeeded()
  return { loginError, text: (await loginError.innerText()).trim() }
}

test.describe('US1: Register an account', () => {
  test(AC_DESCRIPTION, async ({ page, browser, lifecycle, admin, newUser }, testInfo) => {
    await scenario(AC_DESCRIPTION)
    await allure.description(AC_DESCRIPTION)
    await allure.attachment(
      'Test plan',
      [
        'Data:',
        '- Existing account: created by the harness newUser() fixture through the public registration API.',
        '- Visitor attempt: the existing username with a new unique email and a new unique 12-character password, so only the username conflict can reject it.',
        '- Oracles observed with Playwright MCP (discovery-handoff.json): a conflict keeps Create an account open with the alert "Username or email is already registered." and marks the conflicting field invalid with its own error ("is already taken" under Username); the Sign in page shows the alert "Invalid username or password." for credentials that do not exist; the admin Users table "Registered users" pages through every account.',
        '- Admin persona: the lifecycle-seeded ADMIN (admin fixture), used only in its own browser context to read the Users list.',
        ISOLATION,
        'Steps:',
        '1. As a visitor, open Create an account and enter an already-registered username with a new email and a new password.',
        '2. Select Create account and read the error on the form.',
        '3. Try to sign in with that username and the new password.',
        '4. In a separate browser, sign in as the admin and read the complete Users list.',
        'Visible outcomes:',
        '- a duplicate username is rejected with a clear username conflict error',
        '- no account is created after a conflict rejection',
        'Acceptance mapping:',
        "- AC2 | Story clause: Given a visitor submits a username or email that's already registered | Actor: visitor | Visible outcome: a duplicate username is rejected with a clear username conflict error",
        '- AC2 | Story clause: and no account is created | Actor: visitor | Visible outcome: no account is created after a conflict rejection',
        'Boundary coverage:',
        '- AC2 | Condition: >=12 | Values: 12',
        'Assumptions:',
        '- The attempt uses a policy-valid 12-character password so the rejection can only come from the conflict.',
      ].join('\n'),
      'text/plain',
    )

    const existing = await newUser()
    const attempt = { ...visitor(12), username: existing.username }
    const length = [...attempt.password].length
    const { alert, usernameField, emailField } = await submitConflict(page, attempt)
    await usernameField.scrollIntoViewIfNeeded()
    const alertText = (await alert.innerText()).trim()
    const usernameInvalid = (await usernameField.getAttribute('aria-invalid')) ?? 'not set'
    const emailInvalid = (await emailField.getAttribute('aria-invalid')) ?? 'not set'
    await claim(
      'Assertion: [AC2] actor: visitor | the duplicate username is rejected with a clear username conflict error',
      'form stays on Create an account; alert contains "already registered"; Username invalid with "is already taken"; Email not invalid',
      `${new URL(page.url()).pathname} after a ${length}-character password; alert "${alertText}"; Username aria-invalid=${usernameInvalid}; Email aria-invalid=${emailInvalid}`,
      async () => {
        await expect(page).toHaveURL(/\/register$/)
        await expect(alert).toContainText('already registered')
        await expect(usernameField).toHaveAttribute('aria-invalid', 'true')
        await expect(usernameField).toHaveAccessibleDescription(/is already taken/)
        await expect(emailField).not.toHaveAttribute('aria-invalid', 'true')
      },
    )

    const login = await failedSignIn(page, attempt)
    const adminView = await adminUsersPage(browser, lifecycle, admin, testInfo)
    const listed = await allListedUsers(adminView.page)
    await allure.attachment('observed admin Users list', listed.map((u) => `${u.username} | ${u.email}`).join('\n'), 'text/plain')
    const sameName = listed.filter((u) => u.username === existing.username)
    const newEmail = listed.filter((u) => u.email.toLowerCase() === attempt.email.toLowerCase())
    await claim(
      'Assertion: [AC2] actor: visitor | no account is created: the new password does not sign in and the Users list keeps only the original account',
      `Sign in alert contains "Invalid username or password"; exactly one row "${existing.username} | ${existing.email}"; no row with ${attempt.email}`,
      `Sign in alert "${login.text}" for the ${length}-character password; ${sameName.length} row(s) ${sameName.map((u) => `"${u.username} | ${u.email}"`).join(', ')}; ${newEmail.length} row(s) with ${attempt.email} among ${listed.length} listed users`,
      async () => {
        await expect(login.loginError).toContainText('Invalid username or password')
        expect(sameName).toEqual([{ username: existing.username, email: existing.email }])
        expect(newEmail).toEqual([])
      },
    )
    await adminView.close()
  })

  test(AC_DESCRIPTION.replace(/^AC2:/, 'AC2b:'), async ({ page, browser, lifecycle, admin, newUser }, testInfo) => {
    await scenario(AC_DESCRIPTION.replace(/^AC2:/, 'AC2b:'))
    await allure.description(AC_DESCRIPTION)
    await allure.attachment(
      'Test plan',
      [
        'Data:',
        '- Existing account: created by the harness newUser() fixture through the public registration API.',
        '- Visitor attempt: a new unique username with the existing email and a new unique 12-character password, so only the email conflict can reject it.',
        '- Oracles observed with Playwright MCP (discovery-handoff.json): a conflict keeps Create an account open with the alert "Username or email is already registered." and marks the Email field invalid with the error "is already registered"; the Sign in page shows the alert "Invalid username or password." for credentials that do not exist; the admin Users table "Registered users" pages through every account.',
        '- Admin persona: the lifecycle-seeded ADMIN (admin fixture), used only in its own browser context to read the Users list.',
        ISOLATION,
        'Steps:',
        '1. As a visitor, open Create an account and enter a new username with an already-registered email and a new password.',
        '2. Select Create account and read the error on the form.',
        '3. Try to sign in with the new username and password.',
        '4. In a separate browser, sign in as the admin and read the complete Users list.',
        'Visible outcomes:',
        '- a duplicate email is rejected with a clear email conflict error',
        '- no account is created after a conflict rejection',
        'Acceptance mapping:',
        "- AC2 | Story clause: Given a visitor submits a username or email that's already registered | Actor: visitor | Visible outcome: a duplicate email is rejected with a clear email conflict error",
        '- AC2 | Story clause: and no account is created | Actor: visitor | Visible outcome: no account is created after a conflict rejection',
        'Boundary coverage:',
        '- AC2 | Condition: >=12 | Values: 12',
        'Assumptions:',
        '- The attempt uses a policy-valid 12-character password so the rejection can only come from the conflict.',
      ].join('\n'),
      'text/plain',
    )

    const existing = await newUser()
    const attempt = { ...visitor(12), email: existing.email }
    const length = [...attempt.password].length
    const { alert, usernameField, emailField } = await submitConflict(page, attempt)
    await emailField.scrollIntoViewIfNeeded()
    const alertText = (await alert.innerText()).trim()
    const emailInvalid = (await emailField.getAttribute('aria-invalid')) ?? 'not set'
    const usernameInvalid = (await usernameField.getAttribute('aria-invalid')) ?? 'not set'
    await claim(
      'Assertion: [AC2] actor: visitor | the duplicate email is rejected with a clear email conflict error',
      'form stays on Create an account; alert contains "already registered"; Email invalid with "is already registered"; Username not invalid',
      `${new URL(page.url()).pathname} after a ${length}-character password; alert "${alertText}"; Email aria-invalid=${emailInvalid}; Username aria-invalid=${usernameInvalid}`,
      async () => {
        await expect(page).toHaveURL(/\/register$/)
        await expect(alert).toContainText('already registered')
        await expect(emailField).toHaveAttribute('aria-invalid', 'true')
        await expect(emailField).toHaveAccessibleDescription(/is already registered/)
        await expect(usernameField).not.toHaveAttribute('aria-invalid', 'true')
      },
    )

    const login = await failedSignIn(page, attempt)
    const adminView = await adminUsersPage(browser, lifecycle, admin, testInfo)
    const listed = await allListedUsers(adminView.page)
    await allure.attachment('observed admin Users list', listed.map((u) => `${u.username} | ${u.email}`).join('\n'), 'text/plain')
    const newName = listed.filter((u) => u.username === attempt.username)
    const holders = listed.filter((u) => u.email.toLowerCase() === existing.email.toLowerCase())
    await claim(
      'Assertion: [AC2] actor: visitor | no account is created: the new username does not sign in and is absent from the Users list',
      `Sign in alert contains "Invalid username or password"; no row for ${attempt.username}; only ${existing.username} holds ${existing.email}`,
      `Sign in alert "${login.text}" for the ${length}-character password; ${newName.length} row(s) for ${attempt.username}; ${holders.map((u) => u.username).join(', ') || 'nobody'} holds ${existing.email} among ${listed.length} listed users`,
      async () => {
        await expect(login.loginError).toContainText('Invalid username or password')
        expect(newName).toEqual([])
        expect(holders).toEqual([{ username: existing.username, email: existing.email }])
      },
    )
    await adminView.close()
  })
})
