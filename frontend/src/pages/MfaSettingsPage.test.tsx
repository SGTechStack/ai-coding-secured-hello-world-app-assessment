import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { apiUrl, clearCsrfToken } from '@/lib/api/client'
import type { Profile } from '@/lib/auth/session'
import { problemResponse } from '@/test/msw/problems'
import { server } from '@/test/msw/server'
import { renderApp } from '@/test/renderApp'

const ENROLMENT = apiUrl('/api/mfa/totp/enrolment')
const CONFIRMATION = apiUrl('/api/mfa/totp/enrolment/confirmation')
const SECRET = 'JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP'
/** Eight bytes: the PNG signature. */
const QR_PNG = btoa(String.fromCharCode(0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a))

const admin: Profile = {
  id: '00000000-0000-4000-8000-000000000001',
  username: 'alice-admin',
  role: 'ADMIN',
  passwordChangeRequired: false,
  factors: { held: false, required: true, enrolled: false, rebindRequired: false },
}

const provisioning = {
  otpauthUri: `otpauth://totp/Secured%20Hello%20World:alice-admin?secret=${SECRET}`,
  secretBase32: SECRET,
  qrPng: QR_PNG,
}

let created: Blob[]
let revoked: string[]

beforeEach(() => {
  clearCsrfToken()
  created = []
  revoked = []
  // jsdom has no object-URL API.
  vi.spyOn(URL, 'createObjectURL').mockImplementation((blob: Blob | MediaSource) => {
    created.push(blob as Blob)
    return `blob:http://localhost/qr-${created.length}`
  })
  vi.spyOn(URL, 'revokeObjectURL').mockImplementation((url: string) => {
    revoked.push(url)
  })
  server.use(http.get(apiUrl('/api/profile'), () => HttpResponse.json(admin)))
})

afterEach(() => vi.restoreAllMocks())

async function generate() {
  renderApp('/settings/mfa')
  const button = await screen.findByRole('button', { name: 'Generate QR code' })
  // Disabled until the self-read says the factor is not enrolled (T-FE-015).
  await waitFor(() => expect(button).toBeEnabled())
  await userEvent.setup().click(button)
}

describe('/settings/mfa', () => {
  it('T-FE-025: Generate calls provisioning and shows the PNG through a blob: URL, never data:', async () => {
    let provisioned = 0
    server.use(
      http.post(ENROLMENT, () => {
        provisioned += 1
        return HttpResponse.json(provisioning)
      }),
    )

    await generate()

    const image = await screen.findByRole('img', { name: 'QR code for your authenticator app' })
    await waitFor(() => expect(image).toHaveAttribute('src'))
    expect(provisioned).toBe(1)
    expect(image.getAttribute('src')).toMatch(/^blob:/)
    expect(image.getAttribute('src')).not.toMatch(/^data:/)
    expect(created).toHaveLength(1)
    expect(created[0].type).toBe('image/png')
    expect(new Uint8Array(await created[0].arrayBuffer())).toEqual(
      new Uint8Array([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    )
    // The manual-entry secret, for an administrator who cannot scan (R-FE-005).
    expect(screen.getByText('JBSW Y3DP EHPK 3PXP JBSW Y3DP EHPK 3PXP')).toBeInTheDocument()
  })

  it('T-FE-027: a code the server accepts shows the enrolled state and drops the QR code', async () => {
    let body: unknown
    server.use(
      http.post(ENROLMENT, () => HttpResponse.json(provisioning)),
      http.post(CONFIRMATION, async ({ request }) => {
        body = await request.json()
        return new HttpResponse(null, { status: 204 })
      }),
    )
    await generate()
    await screen.findByRole('img', { name: 'QR code for your authenticator app' })

    const user = userEvent.setup()
    await user.type(screen.getByLabelText('Code from the app'), '123456')
    await user.click(screen.getByRole('button', { name: 'Confirm' }))

    expect(await screen.findByRole('status')).toHaveTextContent('Your authenticator app is set up.')
    expect(body).toEqual({ code: '123456' })
    expect(screen.queryByRole('img', { name: 'QR code for your authenticator app' })).not.toBeInTheDocument()
    expect(screen.queryByText(/JBSW/)).not.toBeInTheDocument()
    await waitFor(() => expect(revoked).toEqual(['blob:http://localhost/qr-1']))
  })

  it('T-FE-027: INVALID_FACTOR shows the in-page error on the code and does not enrol', async () => {
    server.use(
      http.post(ENROLMENT, () => HttpResponse.json(provisioning)),
      http.post(CONFIRMATION, () => problemResponse('INVALID_FACTOR', '/api/mfa/totp/enrolment/confirmation')),
    )
    await generate()
    await screen.findByRole('img', { name: 'QR code for your authenticator app' })

    const user = userEvent.setup()
    await user.type(screen.getByLabelText('Code from the app'), '654321')
    await user.click(screen.getByRole('button', { name: 'Confirm' }))

    const code = screen.getByLabelText('Code from the app')
    expect(await screen.findByText(/That code was not accepted/)).toBeInTheDocument()
    expect(code).toHaveAttribute('aria-invalid', 'true')
    expect(code).toHaveFocus()
    expect(screen.queryByText(/Your authenticator app is set up/)).not.toBeInTheDocument()
    expect(screen.getByRole('img', { name: 'QR code for your authenticator app' })).toBeInTheDocument()
  })

  it('refuses a code that is not six digits without calling the server', async () => {
    let confirmations = 0
    server.use(
      http.post(ENROLMENT, () => HttpResponse.json(provisioning)),
      http.post(CONFIRMATION, () => {
        confirmations += 1
        return new HttpResponse(null, { status: 204 })
      }),
    )
    await generate()
    await screen.findByRole('img', { name: 'QR code for your authenticator app' })

    const user = userEvent.setup()
    await user.type(screen.getByLabelText('Code from the app'), '12a45')
    await user.click(screen.getByRole('button', { name: 'Confirm' }))

    expect(await screen.findByText('Enter the 6-digit code from your authenticator app.')).toBeInTheDocument()
    expect(confirmations).toBe(0)
  })

  it('T-FE-015: when the status probe fails the enrolment state is unknown and Generate stays disabled', async () => {
    let provisioned = 0
    server.use(
      http.get(apiUrl('/api/profile'), () => problemResponse('INTERNAL_ERROR', '/api/profile')),
      http.post(ENROLMENT, () => {
        provisioned += 1
        return HttpResponse.json(provisioning)
      }),
    )

    renderApp('/settings/mfa')

    expect(await screen.findByText(/two-factor status could not be checked/)).toBeInTheDocument()
    const button = screen.getByRole('button', { name: 'Generate QR code' })
    expect(button).toBeDisabled()
    await userEvent.setup().click(button)
    expect(provisioned).toBe(0)
    expect(screen.queryByRole('img')).not.toBeInTheDocument()
  })

  it('T-FE-015: while the status probe is in flight Generate is disabled, not offered as for an unenrolled admin', async () => {
    let answer: (() => void) | undefined
    server.use(
      http.get(
        apiUrl('/api/profile'),
        () =>
          new Promise<Response>((resolve) => {
            answer = () => resolve(HttpResponse.json(admin))
          }),
      ),
    )

    renderApp('/settings/mfa')

    const button = await screen.findByRole('button', { name: 'Generate QR code' })
    expect(button).toBeDisabled()
    await waitFor(() => expect(answer).toBeDefined())
    answer!()
    await waitFor(() => expect(button).toBeEnabled())
  })

  it('T-FE-026: with a key already enrolled Generate is disabled and nothing offers to regenerate it', async () => {
    let provisioned = 0
    server.use(
      http.get(apiUrl('/api/profile'), () =>
        HttpResponse.json({ ...admin, factors: { ...admin.factors, enrolled: true } }),
      ),
      http.post(ENROLMENT, () => {
        provisioned += 1
        return HttpResponse.json(provisioning)
      }),
    )

    renderApp('/settings/mfa')

    expect(await screen.findByText(/already set up for your account/)).toBeInTheDocument()
    const button = screen.getByRole('button', { name: 'Generate QR code' })
    expect(button).toBeDisabled()
    await userEvent.setup().click(button)
    expect(provisioned).toBe(0)
    expect(screen.queryByText(/regenerate/i)).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /new QR code/i })).not.toBeInTheDocument()
    expect(screen.queryByText(/old (code|authenticator|TOTP)|stop working|no longer work/i)).not.toBeInTheDocument()
  })

  it('T-FE-016: FACTOR_ALREADY_ENROLLED on provisioning gets its own copy, not the generic failure', async () => {
    server.use(http.post(ENROLMENT, () => problemResponse('FACTOR_ALREADY_ENROLLED', '/api/mfa/totp/enrolment')))

    await generate()

    expect(await screen.findByRole('alert')).toHaveTextContent('An authenticator is already set up for your account.')
    expect(screen.getByRole('alert')).not.toHaveTextContent('could not be generated')
    expect(screen.queryByRole('img')).not.toBeInTheDocument()
  })

  it('shows the generic failure, and no QR code, when provisioning fails otherwise', async () => {
    server.use(http.post(ENROLMENT, () => problemResponse('INTERNAL_ERROR', '/api/mfa/totp/enrolment')))

    await generate()

    expect(await screen.findByRole('alert')).toHaveTextContent('The QR code could not be generated. Try again.')
    expect(screen.queryByRole('img')).not.toBeInTheDocument()
    expect(created).toEqual([])
  })

  it('follows PASSWORD_CHANGE_REQUIRED to the change-password page', async () => {
    server.use(http.post(ENROLMENT, () => problemResponse('PASSWORD_CHANGE_REQUIRED', '/api/mfa/totp/enrolment')))

    await generate()

    expect(await screen.findByRole('heading', { name: 'Change password' })).toBeInTheDocument()
  })

  it('sends a non-administrator to the greeting', async () => {
    server.use(
      http.get(apiUrl('/api/profile'), () => HttpResponse.json({ ...admin, role: 'USER' })),
      http.get(apiUrl('/api/hello'), () => HttpResponse.json({ message: 'Hello, bob' })),
    )

    const { router } = renderApp('/settings/mfa')

    expect(await screen.findByRole('heading', { name: 'Hello, bob' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/hello')
  })
})

describe('/settings/mfa, after ticket 17', () => {
  it('shows its own copy for FACTOR_ALREADY_ENROLLED at confirmation', async () => {
    server.use(
      http.post(ENROLMENT, () => HttpResponse.json(provisioning)),
      http.post(CONFIRMATION, () => problemResponse('FACTOR_ALREADY_ENROLLED', '/api/mfa/totp/enrolment/confirmation')),
    )
    await generate()
    await screen.findByRole('img', { name: 'QR code for your authenticator app' })

    const user = userEvent.setup()
    await user.type(screen.getByLabelText('Code from the app'), '123456')
    await user.click(screen.getByRole('button', { name: 'Confirm' }))

    await waitFor(() =>
      expect(screen.getAllByRole('alert').some((alert) => /already set up/.test(alert.textContent ?? ''))).toBe(true),
    )
    expect(screen.queryByText(/could not be checked/)).not.toBeInTheDocument()
  })

  it('treats a provisioning body that is not the envelope as a failed generation, decoding nothing', async () => {
    server.use(http.post(ENROLMENT, () => HttpResponse.json({ ...provisioning, qrPng: '%%% not base64 %%%' })))

    await generate()

    expect(await screen.findByRole('alert')).toHaveTextContent('The QR code could not be generated. Try again.')
    expect(screen.queryByRole('img')).not.toBeInTheDocument()
    expect(created).toEqual([])
  })

  it('re-reads the self-read after enrolment, whose factor is now held, and offers the user list', async () => {
    let profileReads = 0
    server.use(
      http.get(apiUrl('/api/profile'), () => {
        profileReads += 1
        return HttpResponse.json(admin)
      }),
      http.post(ENROLMENT, () => HttpResponse.json(provisioning)),
      http.post(CONFIRMATION, () => new HttpResponse(null, { status: 204 })),
    )
    await generate()
    await screen.findByRole('img', { name: 'QR code for your authenticator app' })
    const readsBefore = profileReads

    const user = userEvent.setup()
    await user.type(screen.getByLabelText('Code from the app'), '123456')
    await user.click(screen.getByRole('button', { name: 'Confirm' }))

    expect(await screen.findByRole('link', { name: 'Continue to the user list' })).toHaveAttribute(
      'href',
      '/admin/users',
    )
    await waitFor(() => expect(profileReads).toBeGreaterThan(readsBefore))
  })

  it('labels the manual-entry key by its surrounding text, not by aria-labelledby on code', async () => {
    server.use(http.post(ENROLMENT, () => HttpResponse.json(provisioning)))

    await generate()

    const key = await screen.findByText('JBSW Y3DP EHPK 3PXP JBSW Y3DP EHPK 3PXP')
    expect(key.tagName).toBe('CODE')
    expect(key).not.toHaveAttribute('aria-labelledby')
  })
})
