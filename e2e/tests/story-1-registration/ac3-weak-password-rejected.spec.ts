import crypto from 'node:crypto'
import type { Browser, Page, TestInfo } from '@playwright/test'
import { allure } from 'allure-playwright'
import { expect, test, type Persona } from '../../fixtures/test'
import type { Lifecycle } from '../../fixtures/lifecycle'

const campaignSuite = process.env.E2E_CAMPAIGN_SUITE
if (!campaignSuite) throw new Error('E2E_CAMPAIGN_SUITE is required')

const STORY = '**Story 1** — As a **visitor**, I want to register an account with a username, email, and password, so that I can log in and access the protected app.\n\n- Given a visitor submits a unique username, unique email, and a password meeting the minimum strength policy (length ≥ 12), when they submit registration, then an account is created with role `USER`, `enabled = true`, and the password stored as a BCrypt hash.\n- Given a visitor submits a username or email that\'s already registered, when they submit registration, then the request is rejected with a clear validation error (username/email conflict), and no account is created.\n- Given a visitor submits a password that fails the strength policy, when they submit registration, then the request is rejected with a validation error and no account is created.\n- Given any registration attempt, when handled, then the plaintext password is never logged or stored.\n\n### Login'
const AC_DESCRIPTION = 'AC3: Given a visitor submits a password that fails the strength policy, when they submit registration, then the request is rejected with a validation error and no account is created.'
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

test.describe('US1: Register an account', () => {
  test(AC_DESCRIPTION, async ({ page, browser, lifecycle, admin }, testInfo) => {
    await scenario(AC_DESCRIPTION)
    await allure.description(AC_DESCRIPTION)
    await allure.attachment(
      'Test plan',
      [
        'Data:',
        '- Visitor: a generated unique username and email, and a generated unique password of exactly 11 characters (one below length ≥ 12).',
        '- Oracles observed with Playwright MCP (discovery-handoff.json): with an 11-character password, Create an account stays open, the Password field is marked invalid and shows the error "Use at least 12 characters."; the Sign in page shows the alert "Invalid username or password." for credentials that do not exist; the admin Users table "Registered users" pages through every account.',
        '- Admin persona: the lifecycle-seeded ADMIN (admin fixture), used only in its own browser context to read the Users list.',
        ISOLATION,
        'Steps:',
        '1. As a visitor, open Create an account and enter a unique username and email with an 11-character password twice.',
        '2. Select Create account and read the error on the Password field.',
        '3. Try to sign in with that username and password.',
        '4. In a separate browser, sign in as the admin and read the complete Users list.',
        'Visible outcomes:',
        '- an 11-character password is rejected with a validation error',
        '- no account is created after a weak-password rejection',
        'Acceptance mapping:',
        '- AC3 | Story clause: Given a visitor submits a password that fails the strength policy | Actor: visitor | Visible outcome: an 11-character password is rejected with a validation error',
        '- AC3 | Story clause: and no account is created | Actor: visitor | Visible outcome: no account is created after a weak-password rejection',
        'Boundary coverage:',
        '- AC3 | Condition: >=12 | Values: 12',
        'Assumptions:',
        '- The submitted value is 11, one below the story threshold; the boundary row names the story threshold 12, which the rendered error states. The acceptance of exactly 12 is proved by AC1.',
        '- The form rejects the weak password before sending it; the server-side policy check is not browser-visible and is not claimed here.',
      ].join('\n'),
      'text/plain',
    )

    const person = visitor(11)
    const length = [...person.password].length
    await fillRegistration(page, person)
    const passwordField = page.getByLabel('Password', { exact: true })
    const invalidBefore = (await passwordField.getAttribute('aria-invalid')) ?? 'not set'
    await page.getByRole('button', { name: 'Create account' }).click()
    const fieldError = page.getByText('Use at least 12 characters.')
    await fieldError.waitFor()
    await passwordField.scrollIntoViewIfNeeded()
    const invalidAfter = (await passwordField.getAttribute('aria-invalid')) ?? 'not set'
    const errorText = (await fieldError.innerText()).trim()
    await claim(
      'Assertion: [AC3] actor: visitor | the 11-character password is rejected with a password validation error',
      'form stays on Create an account; Password aria-invalid changes from not set to true; Password error "Use at least 12 characters."',
      `${new URL(page.url()).pathname} after a ${length}-character password; Password aria-invalid ${invalidBefore} -> ${invalidAfter}; Password error "${errorText}"`,
      async () => {
        expect(invalidBefore).toBe('not set')
        await expect(page).toHaveURL(/\/register$/)
        await expect(passwordField).toHaveAttribute('aria-invalid', 'true')
        await expect(passwordField).toHaveAccessibleDescription(/Use at least 12 characters\./)
        await expect(fieldError).toBeVisible()
      },
    )

    await signIn(page, person.username, person.password)
    const loginError = page.getByRole('alert')
    await loginError.waitFor()
    await loginError.scrollIntoViewIfNeeded()
    const loginText = (await loginError.innerText()).trim()
    const adminView = await adminUsersPage(browser, lifecycle, admin, testInfo)
    const listed = await allListedUsers(adminView.page)
    await allure.attachment('observed admin Users list', listed.map((u) => `${u.username} | ${u.email}`).join('\n'), 'text/plain')
    const matches = listed.filter(
      (u) => u.username === person.username || u.email.toLowerCase() === person.email.toLowerCase(),
    )
    await claim(
      'Assertion: [AC3] actor: visitor | no account is created: the credentials do not sign in and the username is absent from the Users list',
      `Sign in alert contains "Invalid username or password"; no row for ${person.username} or ${person.email} (policy needs at least 12 characters)`,
      `Sign in alert "${loginText}" for the ${length}-character password; ${matches.length} row(s) for ${person.username} or ${person.email} among ${listed.length} listed users (policy error said "${errorText}")`,
      async () => {
        await expect(loginError).toContainText('Invalid username or password')
        expect(matches).toEqual([])
      },
    )
    await adminView.close()
  })
})
