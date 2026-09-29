import { zodResolver } from '@hookform/resolvers/zod'
import { useQuery } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, Navigate } from 'react-router'
import { z } from 'zod'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { ApiError, type ErrorCode } from '@/lib/api/errors'
import { fetchProfile, PROFILE_KEY } from '@/lib/auth/session'
import { confirmTotp, groupSecret, type Provisioning, provisionTotp, qrPngBlob } from '@/lib/mfa/enrolment'
import { useObjectUrlImage } from '@/lib/mfa/useObjectUrlImage'

const schema = z.object({
  code: z.string().regex(/^\d{6}$/, 'Enter the 6-digit code from your authenticator app.'),
})

type ConfirmationForm = z.infer<typeof schema>

const INVALID_CODE = 'That code was not accepted. Check the code in your authenticator app and try again.'
const PROVISIONING_COPY: Partial<Record<ErrorCode, string>> = {
  FACTOR_ALREADY_ENROLLED:
    'An authenticator is already set up for your account. Another administrator must reset it before you can set up a new one.',
  TOO_MANY_REQUESTS: 'Too many attempts. Wait a moment, then try again.',
}
const GENERIC_PROVISIONING = 'The QR code could not be generated. Try again.'
const CONFIRMATION_COPY: Partial<Record<ErrorCode, string>> = {
  TOO_MANY_REQUESTS: 'Too many attempts. Wait a moment, then try again.',
}
const GENERIC_CONFIRMATION = 'The code could not be checked. Try again.'

/**
 * `/settings/mfa`: TOTP enrolment, for administrators only (ADR-023). Generate shows the server's QR code through a
 * `blob:` URL and the same secret for manual entry (ADR-025; R-FE-005); a code from the app confirms it. The secret
 * lives in component state only, and is dropped once enrolment is confirmed or fails.
 */
export function MfaSettingsPage() {
  const profile = useQuery({ queryKey: PROFILE_KEY, queryFn: fetchProfile })
  const [provisioning, setProvisioning] = useState<Provisioning>()
  const [generating, setGenerating] = useState(false)
  const [failure, setFailure] = useState('')
  const [redirect, setRedirect] = useState<string>()
  const [enrolled, setEnrolled] = useState(false)
  const qrBlob = useMemo(() => (provisioning ? qrPngBlob(provisioning.qrPng) : undefined), [provisioning])
  const qrImage = useObjectUrlImage(qrBlob)
  const {
    register,
    handleSubmit,
    setError,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<ConfirmationForm>({ resolver: zodResolver(schema), defaultValues: { code: '' } })

  // Authority: whatever the client believed, the server's code decides that the session is over.
  if (profile.error instanceof ApiError && profile.error.code === 'AUTHENTICATION_FAILED') {
    return <Navigate to="/sign-in" replace />
  }
  if (redirect) {
    return <Navigate to={redirect} replace />
  }
  // A UX guard only: the server refuses a non-administrator whatever renders here.
  if (profile.data && profile.data.role !== 'ADMIN') {
    return <Navigate to="/hello" replace />
  }

  /** Follows a code that says the page cannot be used at all; true when it did. */
  const followAuthority = (error: unknown) => {
    if (error instanceof ApiError && error.code === 'AUTHENTICATION_FAILED') {
      setRedirect('/sign-in')
      return true
    }
    if (error instanceof ApiError && error.code === 'PASSWORD_CHANGE_REQUIRED') {
      setRedirect('/change-password')
      return true
    }
    return false
  }

  const onGenerate = async () => {
    setFailure('')
    setGenerating(true)
    try {
      setProvisioning(await provisionTotp())
      reset()
    } catch (error) {
      setProvisioning(undefined)
      if (!followAuthority(error)) {
        setFailure((error instanceof ApiError && PROVISIONING_COPY[error.code]) || GENERIC_PROVISIONING)
      }
    } finally {
      setGenerating(false)
    }
  }

  const onConfirm = async ({ code }: ConfirmationForm) => {
    setFailure('')
    try {
      await confirmTotp(code)
      setProvisioning(undefined)
      setEnrolled(true)
    } catch (error) {
      if (error instanceof ApiError && error.code === 'INVALID_FACTOR') {
        setError('code', { message: INVALID_CODE }, { shouldFocus: true })
      } else if (!followAuthority(error)) {
        setFailure((error instanceof ApiError && CONFIRMATION_COPY[error.code]) || GENERIC_CONFIRMATION)
      }
    }
  }

  return (
    <section aria-labelledby="mfa-heading" className="mt-6 flex flex-col gap-4">
      <h2 id="mfa-heading" className="text-xl font-semibold">
        Two-factor authentication
      </h2>
      {enrolled ? (
        <p role="status" className="text-sm">
          Your authenticator app is set up. You will be asked for a code from it to use the administrator pages.
        </p>
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
      {provisioning && (
        <>
          <img ref={qrImage} alt="QR code for your authenticator app" width={240} height={240} className="border" />
          <div className="flex flex-col gap-1">
            <p className="text-sm" id="manual-secret-label">
              Can’t scan it? Enter this key in the app instead (time-based, 6 digits, every 30 seconds):
            </p>
            <code aria-labelledby="manual-secret-label" className="font-mono text-base break-all">
              {groupSecret(provisioning.secretBase32)}
            </code>
          </div>
          <form noValidate onSubmit={handleSubmit(onConfirm)} className="flex flex-col gap-4">
            <div className="flex flex-col gap-2">
              <Label htmlFor="totp-code">Code from the app</Label>
              <Input
                id="totp-code"
                inputMode="numeric"
                autoComplete="one-time-code"
                maxLength={6}
                aria-invalid={errors.code ? true : undefined}
                aria-describedby={errors.code ? 'totp-code-error' : undefined}
                {...register('code')}
              />
              {errors.code && (
                <p id="totp-code-error" className="text-sm text-destructive">
                  {errors.code.message}
                </p>
              )}
            </div>
            <div>
              <Button type="submit" disabled={isSubmitting}>
                Confirm
              </Button>
            </div>
          </form>
        </>
      )}
      {/* Always rendered, so a screen reader announces the message when it appears (live region). */}
      <p role="alert" className="text-sm text-destructive">
        {failure}
      </p>
      <Link to="/hello" className="text-sm underline">
        Back
      </Link>
    </section>
  )
}
