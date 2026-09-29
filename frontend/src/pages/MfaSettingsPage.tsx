import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { Link, Navigate } from 'react-router'
import { INVALID_CODE, TOO_MANY_ATTEMPTS, TotpCodeForm } from '@/components/TotpCodeForm'
import { Button } from '@/components/ui/button'
import { ApiError, type ErrorCode } from '@/lib/api/errors'
import { authorityRoute, useAuthorityFailure } from '@/lib/auth/authority'
import { fetchProfile, PROFILE_KEY } from '@/lib/auth/session'
import { confirmTotp, groupSecret, type Provisioning, provisionTotp, qrPngBlob } from '@/lib/mfa/enrolment'
import { useObjectUrlImage } from '@/lib/mfa/useObjectUrlImage'

const ALREADY_ENROLLED =
  'An authenticator is already set up for your account. Another administrator must reset it before you can set up a new one.'
const COPY: Partial<Record<ErrorCode, string>> = {
  FACTOR_ALREADY_ENROLLED: ALREADY_ENROLLED,
  TOO_MANY_REQUESTS: TOO_MANY_ATTEMPTS,
}
const GENERIC_PROVISIONING = 'The QR code could not be generated. Try again.'
const GENERIC_CONFIRMATION = 'The code could not be checked. Try again.'

/**
 * `/settings/mfa`: TOTP enrolment, for administrators only (ADR-023). Generate shows the server's QR code through a
 * `blob:` URL and the same secret for manual entry (ADR-025; R-FE-005); a code from the app confirms it. The secret
 * lives in component state only, and is dropped once enrolment is confirmed or fails.
 */
export function MfaSettingsPage() {
  const queryClient = useQueryClient()
  const profile = useQuery({ queryKey: PROFILE_KEY, queryFn: fetchProfile })
  const [provisioning, setProvisioning] = useState<Provisioning>()
  const [generating, setGenerating] = useState(false)
  const [enrolled, setEnrolled] = useState(false)
  const { redirect, failure, fail, clearFailure } = useAuthorityFailure()

  // Authority: whatever the client believed, the server's code decides that the session is over.
  const leaving = redirect ?? authorityRoute(profile.error)
  if (leaving) {
    return <Navigate to={leaving} replace />
  }
  // A UX guard only: the server refuses a non-administrator whatever renders here.
  if (profile.data && profile.data.role !== 'ADMIN') {
    return <Navigate to="/hello" replace />
  }

  const onGenerate = async () => {
    clearFailure()
    setGenerating(true)
    try {
      setProvisioning(await provisionTotp())
    } catch (error) {
      setProvisioning(undefined)
      fail(error, COPY, GENERIC_PROVISIONING)
    } finally {
      setGenerating(false)
    }
  }

  const onCode = async (code: string) => {
    clearFailure()
    try {
      await confirmTotp(code)
      setProvisioning(undefined)
      setEnrolled(true)
      // Enrolment binding granted the factor, so the self-read's factor state has changed.
      await queryClient.invalidateQueries({ queryKey: PROFILE_KEY })
      return undefined
    } catch (error) {
      if (error instanceof ApiError && error.code === 'INVALID_FACTOR') {
        return INVALID_CODE
      }
      fail(error, COPY, GENERIC_CONFIRMATION)
      return undefined
    }
  }

  return (
    <section aria-labelledby="mfa-heading" className="mt-6 flex flex-col gap-4">
      <h2 id="mfa-heading" className="text-xl font-semibold">
        Two-factor authentication
      </h2>
      {enrolled ? (
        <>
          <p role="status" className="text-sm">
            Your authenticator app is set up. You will be asked for a code from it to use the administrator pages.
          </p>
          <Link to="/admin/users" className="text-sm underline">
            Continue to the user list
          </Link>
        </>
      ) : (
        <>
          <p className="text-sm">
            Administrators must use an authenticator app. Generate a QR code, scan it with the app, then enter the
            6-digit code the app shows.
          </p>
          <div>
            <Button type="button" onClick={onGenerate} disabled={generating}>
              {provisioning ? 'Generate a new QR code' : 'Generate QR code'}
            </Button>
          </div>
        </>
      )}
      {provisioning && <ProvisionedSecret provisioning={provisioning} onCode={onCode} />}
      {/* Always rendered, so a screen reader announces the message when it appears (live region). */}
      <p role="alert" className="text-sm text-destructive">
        {failure}
      </p>
      <Link to="/" className="text-sm underline">
        Back
      </Link>
    </section>
  )
}

/** The QR code, the same secret for manual entry, and the confirmation form. */
function ProvisionedSecret({
  provisioning,
  onCode,
}: {
  provisioning: Provisioning
  onCode: (code: string) => Promise<string | undefined>
}) {
  const qrBlob = useMemo(() => qrPngBlob(provisioning.qrPng), [provisioning])
  const qrImage = useObjectUrlImage(qrBlob)

  return (
    <>
      <img ref={qrImage} alt="QR code for your authenticator app" width={240} height={240} className="border" />
      <div className="flex flex-col gap-1">
        <p className="text-sm">
          Can’t scan it? Enter this key in the app instead (time-based, 6 digits, every 30 seconds):
        </p>
        <code className="font-mono text-base break-all">{groupSecret(provisioning.secretBase32)}</code>
      </div>
      <TotpCodeForm key={provisioning.secretBase32} submitLabel="Confirm" onCode={onCode} />
    </>
  )
}
