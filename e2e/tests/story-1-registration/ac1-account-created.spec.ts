import crypto from 'node:crypto'
import type { Browser, Page, TestInfo } from '@playwright/test'
import { allure } from 'allure-playwright'
import { expect, test, type Persona } from '../../fixtures/test'
import type { Lifecycle } from '../../fixtures/lifecycle'

const campaignSuite = process.env.E2E_CAMPAIGN_SUITE
if (!campaignSuite) throw new Error('E2E_CAMPAIGN_SUITE is required')

const STORY = '**Story 1** — As a **visitor**, I want to register an account with a username, email, and password, so that I can log in and access the protected app.\n\n- Given a visitor submits a unique username, unique email, and a password meeting the minimum strength policy (length ≥ 12), when they submit registration, then an account is created with role `USER`, `enabled = true`, and the password stored as a BCrypt hash.\n- Given a visitor submits a username or email that\'s already registered, when they submit registration, then the request is rejected with a clear validation error (username/email conflict), and no account is created.\n- Given a visitor submits a password that fails the strength policy, when they submit registration, then the request is rejected with a validation error and no account is created.\n- Given any registration attempt, when handled, then the plaintext password is never logged or stored.\n\n### Login'
const AC_DESCRIPTION = 'AC1: Given a visitor submits a unique username, unique email, and a password meeting the minimum strength policy (length ≥ 12), when they submit registration, then an account is created with role `USER`, `enabled = true`, and the password stored as a BCrypt hash.'
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

/** Pages through the admin Users list until the row for `username` is on screen. */
async function adminRowFor(page: Page, username: string) {
  const pager = page.getByRole('navigation', { name: 'Pagination' })
  const row = page
    .getByRole('table', { name: 'Registered users' })
    .getByRole('row')
    .filter({ has: page.getByRole('rowheader', { name: username, exact: true }) })
  for (let n = 1; ; n++) {
    await expect(pager).toContainText(`Page ${n} of `)
    if ((await row.count()) === 1) {
      await row.scrollIntoViewIfNeeded()
      return row
    }
    const next = pager.getByRole('button', { name: 'Next' })
    if (await next.isDisabled()) throw new Error(`the Users list has no row for ${username}`)
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
        '- Visitor: a generated unique username and email, and a generated unique password of exactly 12 characters (the lower bound of length ≥ 12).',
        '- Oracles observed with Playwright MCP (discovery-handoff.json): Create an account form with Username, Email, Password, Confirm password and a Create account button; after success the Sign in page shows the status "Account created. You can now sign in."; a signed-in user sees the heading "Hello, <username>" and the Main navigation shows the role badge; the admin Users table "Registered users" has a "Role for <username>" select and a Status cell per row.',
        '- Admin persona: the lifecycle-seeded ADMIN (admin fixture), used only in its own browser context to read the Users list.',
        ISOLATION,
        'Steps:',
        '1. As a visitor, open Create an account and enter a unique username, a unique email and a 12-character password twice.',
        '2. Select Create account and read the notice on the Sign in page.',
        '3. Sign in with the new username and password and read the greeting and the role badge.',
        '4. In a separate browser, sign in as the admin, open Users and read the new account’s Role and Status.',
        'Visible outcomes:',
        '- registration succeeds and the new account can sign in',
        '- a password of exactly 12 characters is accepted',
        '- the new account has role USER',
        '- the new account is enabled',
        'Acceptance mapping:',
        '- AC1 | Story clause: then an account is created | Actor: visitor | Visible outcome: registration succeeds and the new account can sign in',
        '- AC1 | Story clause: a password meeting the minimum strength policy (length ≥ 12) | Actor: visitor | Visible outcome: a password of exactly 12 characters is accepted',
        '- AC1 | Story clause: with role `USER` | Actor: visitor | Visible outcome: the new account has role USER',
        '- AC1 | Story clause: `enabled = true` | Actor: visitor | Visible outcome: the new account is enabled',
        'Boundary coverage:',
        '- AC1 | Condition: >=12 | Values: 12',
        'Assumptions:',
        '- The stored BCrypt hash has no permitted browser surface; it is reported by the separate skipped AC1b scenario.',
      ].join('\n'),
      'text/plain',
    )

    const person = visitor(12)
    await fillRegistration(page, person)
    const registered = registerResponse(page)
    await page.getByRole('button', { name: 'Create account' }).click()
    await registered
    await page.waitForURL('**/login')
    const notice = page.getByRole('status')
    await notice.scrollIntoViewIfNeeded()
    const noticeText = (await notice.innerText()).trim()
    const length = [...person.password].length
    await claim(
      'Assertion: [AC1] actor: visitor | the 12-character password is accepted and the Sign in page confirms the account was created',
      'a 12-character password leads to Sign in with a status containing "Account created"',
      `a ${length}-character password led to ${new URL(page.url()).pathname} with status "${noticeText}"`,
      async () => {
        expect(length).toBe(12)
        await expect(page).toHaveURL(/\/login$/)
        await expect(notice).toContainText('Account created')
      },
    )

    await page.getByLabel('Username').fill(person.username)
    await page.getByLabel('Password').fill(person.password)
    await page.getByRole('button', { name: 'Sign in' }).click()
    const greeting = page.getByRole('heading', { level: 1, name: `Hello, ${person.username}` })
    await greeting.waitFor()
    await greeting.scrollIntoViewIfNeeded()
    const greetingText = (await page.getByRole('heading', { level: 1 }).innerText()).trim()
    await claim(
      'Assertion: [AC1] actor: visitor | the new account signs in with its registered username and 12-character password',
      `heading "Hello, ${person.username}" after signing in with the 12-character password`,
      `heading "${greetingText}" after signing in with the ${length}-character password`,
      async () => expect(greeting).toBeVisible(),
    )

    const nav = page.getByRole('navigation', { name: 'Main' })
    await nav.scrollIntoViewIfNeeded()
    const navText = (await nav.innerText()).replace(/\s+/g, ' ').trim()
    await claim(
      'Assertion: [AC1] actor: visitor | the new account’s own header shows role USER',
      `Main navigation shows ${person.username} with the badge USER (12-character password account)`,
      `Main navigation "${navText}" (${length}-character password account)`,
      async () => {
        await expect(nav).toContainText(person.username)
        await expect(nav.getByText('USER', { exact: true })).toBeVisible()
      },
    )

    const adminView = await adminUsersPage(browser, lifecycle, admin, testInfo)
    const row = await adminRowFor(adminView.page, person.username)
    const role = row.getByRole('combobox', { name: `Role for ${person.username}` })
    const roleValue = await role.inputValue()
    await claim(
      'Assertion: [AC1] actor: visitor | the admin Users list shows the new account with role USER',
      `Role for ${person.username} = USER`,
      `Role for ${person.username} = ${roleValue} (registered with a ${length}-character password)`,
      async () => expect(role).toHaveValue('USER'),
    )
    const status = row.getByRole('cell').nth(2)
    const statusText = (await status.innerText()).trim()
    await claim(
      'Assertion: [AC1] actor: visitor | the admin Users list shows the new account as Enabled',
      `Status for ${person.username} = Enabled`,
      `Status for ${person.username} = ${statusText} (registered with a ${length}-character password)`,
      async () => expect(status).toHaveText('Enabled'),
    )
    await adminView.close()
  })

  test(AC_DESCRIPTION.replace(/^AC1:/, 'AC1b:'), async ({ page }) => {
    const REASON =
      'Blocked: the story needs the stored password to be a BCrypt hash, but no permitted browser, API or log surface shows what the database stores; registration returns only the id, username and role, and the admin Users list has no password column. This claim belongs to the backend integration tests for RegistrationService with BCryptPasswordEncoder.'
    await scenario(AC_DESCRIPTION.replace(/^AC1:/, 'AC1b:'))
    await allure.description(`${AC_DESCRIPTION}\n\n${REASON}`)
    await allure.attachment(
      'Test plan',
      [
        'Data:',
        '- Visitor: a generated unique username and email, and a generated unique password of exactly 12 characters.',
        '- Oracle (discovery-handoff.json): the registration response exposes only id, username and role; the H2 database is private to the stack and story specs may not read it.',
        ISOLATION,
        'Steps:',
        '1. As a visitor, open Create an account and enter a unique username, email and 12-character password twice.',
        '2. Select Create account and look for any permitted place that shows the stored password.',
        '3. Record that none exists, and stop.',
        'Visible outcomes:',
        '- the stored password is a BCrypt hash',
        'Acceptance mapping:',
        '- AC1 | Story clause: the password stored as a BCrypt hash | Actor: visitor | Visible outcome: the stored password is a BCrypt hash',
        'Boundary coverage:',
        '- AC1 | Condition: >=12 | Values: 12',
      ].join('\n'),
      'text/plain',
    )

    const person = visitor(12)
    await fillRegistration(page, person)
    const registered = registerResponse(page)
    await page.getByRole('button', { name: 'Create account' }).click()
    const response = await registered
    const fields = Object.keys((await response.json()) as Record<string, unknown>).sort()
    await page.waitForURL('**/login')
    await allure.attachment(
      'blocker evidence',
      [
        REASON,
        '',
        `Registering ${person.username} returned HTTP ${response.status()} with the fields: ${fields.join(', ')}.`,
        'No response field, rendered page, admin Users column or backend log line carries the stored password hash,',
        'and story specs may not read the private H2 database. Proof belongs to the backend test layer',
        '(RegistrationService with BCryptPasswordEncoder writing users.password_hash).',
      ].join('\n'),
      'text/plain',
    )
    test.skip(true, 'No permitted surface shows the stored password hash.')
  })
})
