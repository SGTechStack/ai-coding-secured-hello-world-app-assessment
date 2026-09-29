import { expect, test } from '../../fixtures/test'
import { waitForResetLink } from '../../fixtures/mailbox'

/**
 * Foundation smoke: proves the lifecycle, the admin persona, the newUser fixture and the test
 * mailbox work end to end. It asserts no story outcome.
 */
test.describe('Foundation smoke', () => {
  test('the seeded admin signs in, opens the user list and signs out', async ({ page, admin }) => {
    await page.goto('/')
    await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible()

    await page.getByLabel('Username').fill(admin.username)
    await page.getByLabel('Password').fill(admin.password)
    await page.getByRole('button', { name: 'Sign in' }).click()
    await expect(page.getByRole('heading', { name: `Hello, ${admin.username}` })).toBeVisible()

    await page.getByRole('link', { name: 'Users' }).click()
    await expect(page.getByRole('heading', { name: 'Users' })).toBeVisible()

    await page.getByRole('button', { name: 'Sign out' }).click()
    await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible()
  })

  test('a user created by the newUser fixture can sign in', async ({ page, newUser }) => {
    const user = await newUser()

    await page.goto('/login')
    await page.getByLabel('Username').fill(user.username)
    await page.getByLabel('Password').fill(user.password)
    await page.getByRole('button', { name: 'Sign in' }).click()

    await expect(page.getByRole('heading', { name: `Hello, ${user.username}` })).toBeVisible()
  })

  test('the test mailbox receives a requested reset link', async ({ page, newUser, lifecycle }) => {
    const user = await newUser()

    await page.goto('/forgot-password')
    await page.getByLabel('Email').fill(user.email)
    await page.getByRole('button', { name: 'Send reset link' }).click()
    await expect(page.getByRole('status')).toContainText('If an account exists')

    const link = await waitForResetLink(lifecycle, user.username)
    expect(link).toMatch(new RegExp(`^${lifecycle.webUrl}/reset-password#token=[A-Za-z0-9_-]{43}$`))
  })
})
