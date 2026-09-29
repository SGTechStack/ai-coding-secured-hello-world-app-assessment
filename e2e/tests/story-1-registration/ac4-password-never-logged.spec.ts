import crypto from 'node:crypto'
import fs from 'node:fs'
import type { Page } from '@playwright/test'
import { allure } from 'allure-playwright'
import { expect, test, type Persona } from '../../fixtures/test'

const campaignSuite = process.env.E2E_CAMPAIGN_SUITE
if (!campaignSuite) throw new Error('E2E_CAMPAIGN_SUITE is required')

const STORY = '**Story 1** — As a **visitor**, I want to register an account with a username, email, and password, so that I can log in and access the protected app.\n\n- Given a visitor submits a unique username, unique email, and a password meeting the minimum strength policy (length ≥ 12), when they submit registration, then an account is created with role `USER`, `enabled = true`, and the password stored as a BCrypt hash.\n- Given a visitor submits a username or email that\'s already registered, when they submit registration, then the request is rejected with a clear validation error (username/email conflict), and no account is created.\n- Given a visitor submits a password that fails the strength policy, when they submit registration, then the request is rejected with a validation error and no account is created.\n- Given any registration attempt, when handled, then the plaintext password is never logged or stored.\n\n### Login'
const AC_DESCRIPTION = 'AC4: Given any registration attempt, when handled, then the plaintext password is never logged or stored.'
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

test.describe('US1: Register an account', () => {
  test(AC_DESCRIPTION, async ({ page, lifecycle, newUser }) => {
    await scenario(AC_DESCRIPTION)
    await allure.description(AC_DESCRIPTION)
    await allure.attachment(
      'Test plan',
      [
        'Data:',
        '- Four visitor registration attempts in the browser, each with its own generated unique password: a username conflict and an email conflict against an account created by the harness newUser() fixture, an 11-character password, and finally a successful registration with a 12-character password.',
        '- Surface: the lifecycle backend log file (lifecycle.appLog from the protected foundation fixture, the same file fixtures/mailbox.ts reads). Oracle (discovery-handoff.json): a handled successful registration writes "AUDIT : event=USER_REGISTERED target=<username>".',
        ISOLATION,
        'Steps:',
        '1. As a visitor, try to register an already-registered username, then an already-registered email, each with a new password.',
        '2. As a visitor, try to register with an 11-character password.',
        '3. As a visitor, register successfully with a 12-character password.',
        '4. Once the backend has logged the successful registration, search the whole backend log for every submitted password.',
        'Visible outcomes:',
        '- the plaintext password never appears in the backend log',
        'Acceptance mapping:',
        '- AC4 | Story clause: then the plaintext password is never logged | Actor: visitor | Visible outcome: the plaintext password never appears in the backend log',
        'Boundary coverage:',
        '- AC4 | Condition: >=12 | Values: 12',
        'Assumptions:',
        '- The successful registration is submitted last, so its audit line marks the point by which the backend has handled every earlier attempt.',
        '- The 11-character password is rejected in the browser before it is sent; it is included so no attempt type is left out.',
        '- "or stored" has no permitted surface and is reported by the separate skipped AC4b scenario.',
      ].join('\n'),
      'text/plain',
    )

    const existing = await newUser()
    const usernameConflict = { ...visitor(12), username: existing.username }
    const emailConflict = { ...visitor(12), email: existing.email }
    const weak = visitor(11)
    const success = visitor(12)
    const attempts: [string, Persona][] = [
      ['username conflict', usernameConflict],
      ['email conflict', emailConflict],
      ['11-character password', weak],
      ['successful 12-character registration', success],
    ]

    for (const conflict of [usernameConflict, emailConflict]) {
      await fillRegistration(page, conflict)
      const rejected = registerResponse(page)
      await page.getByRole('button', { name: 'Create account' }).click()
      await rejected
      await expect(page.getByRole('alert')).toContainText('already registered')
    }
    await fillRegistration(page, weak)
    await page.getByRole('button', { name: 'Create account' }).click()
    await expect(page.getByLabel('Password', { exact: true })).toHaveAttribute('aria-invalid', 'true')
    await fillRegistration(page, success)
    const registered = registerResponse(page)
    await page.getByRole('button', { name: 'Create account' }).click()
    await registered
    await page.waitForURL('**/login')
    await expect(page.getByRole('status')).toContainText('Account created')

    const auditLine = `event=USER_REGISTERED target=${success.username}`
    await expect
      .poll(() => fs.readFileSync(lifecycle.appLog, 'utf8').includes(auditLine), {
        message: 'the backend log records the successful registration',
      })
      .toBe(true)
    const log = fs.readFileSync(lifecycle.appLog, 'utf8')
    const related = log
      .split('\n')
      .filter((line) => attempts.some(([, p]) => line.includes(p.username) || line.includes(p.email)))
    await allure.attachment('backend log lines naming these attempts', related.join('\n') || '(none)', 'text/plain')
    const hits = attempts.map(([label, p]) => ({ label, length: [...p.password].length, count: log.split(p.password).length - 1 }))
    await claim(
      'Assertion: [AC4] actor: visitor | no submitted plaintext password appears in the backend log',
      hits.map((h) => `${h.label}: 0 occurrences`).join('; '),
      `${hits.map((h) => `${h.label} (${h.length}-character password): ${h.count} occurrences`).join('; ')}; searched ${log.split('\n').length} log lines including "${auditLine}"`,
      async () => expect(hits.filter((h) => h.count > 0)).toEqual([]),
    )
  })

  test(AC_DESCRIPTION.replace(/^AC4:/, 'AC4b:'), async ({ page }) => {
    const REASON =
      'Blocked: the story needs proof that the plaintext password is never stored, but no permitted browser, API or log surface shows what the database stores; registration returns only the id, username and role. This claim belongs to the backend repository and integration tests.'
    await scenario(AC_DESCRIPTION.replace(/^AC4:/, 'AC4b:'))
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
        '2. Select Create account and look for any permitted place that shows stored password material.',
        '3. Record that none exists, and stop.',
        'Visible outcomes:',
        '- the plaintext password is never stored',
        'Acceptance mapping:',
        '- AC4 | Story clause: or stored | Actor: visitor | Visible outcome: the plaintext password is never stored',
        'Boundary coverage:',
        '- AC4 | Condition: >=12 | Values: 12',
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
        'No response field, rendered page or admin Users column shows stored password material, and story specs may',
        'not read the private H2 database. Proof belongs to backend repository/integration tests of users.password_hash.',
      ].join('\n'),
      'text/plain',
    )
    test.skip(true, 'No permitted surface shows stored password material.')
  })
})
