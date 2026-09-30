import { zodResolver } from '@hookform/resolvers/zod'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { Navigate, useLocation, useNavigate } from 'react-router'
import { z } from 'zod'
import { NewPasswordHints } from '@/components/NewPasswordHints'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { ApiError, type ErrorCode } from '@/lib/api/errors'
import { changePassword, fetchProfile, landingFor, type Profile, PROFILE_KEY, signOut } from '@/lib/auth/session'
import { isPasswordRule, lengthRule, RULE_COPY } from '@/lib/password/policy'

const schema = z.object({
  currentPassword: z.string().min(1, 'Enter your current password.'),
  // Only the two length rules can be checked here; the server runs the whole policy (ADR-005).
  newPassword: z.string().superRefine((value, context) => {
    const rule = lengthRule(value)
    if (rule) {
      context.addIssue({ code: 'custom', message: RULE_COPY[rule] })
    }
  }),
})

type PasswordChangeForm = z.infer<typeof schema>

const WRONG_CURRENT = 'The current password is not correct.'
const FAILURE_COPY: Partial<Record<ErrorCode, string>> = {
  TOO_MANY_REQUESTS: 'Too many attempts. Wait a moment, then try again.',
}
const GENERIC_FAILURE = 'The password could not be changed. Try again.'
const GENERIC_REJECTION = 'The new password was not accepted. Choose another.'

/**
 * Self-service password change (ADR-008). The current password is always required. Paste and password managers work
 * on both fields (R-FE-001). The strength meter is indicative only; the server decides (ADR-005). For a forced-change
 * session (the first gate, ADR-046) it explains why, offers sign-out instead of a way back, and on success moves on.
 */
export function ChangePasswordPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const queryClient = useQueryClient()
  const profile = useQuery({ queryKey: PROFILE_KEY, queryFn: fetchProfile })
  const forced = profile.data?.passwordChangeRequired === true
  const [failure, setFailure] = useState('')
  const [changed, setChanged] = useState(false)
  const {
    register,
    handleSubmit,
    setError,
    reset,
    control,
    formState: { errors, isSubmitting },
  } = useForm<PasswordChangeForm>({
    resolver: zodResolver(schema),
    defaultValues: { currentPassword: '', newPassword: '' },
  })
  const newPassword = useWatch({ control, name: 'newPassword' })
  const username = profile.data?.username

  // Authority: whatever the client believed, the server's code decides that the session is over.
  if (profile.error instanceof ApiError && profile.error.code === 'AUTHENTICATION_FAILED') {
    return <Navigate to="/sign-in" replace />
  }

  const onSubmit = async ({ currentPassword, newPassword: next }: PasswordChangeForm) => {
    setFailure('')
    setChanged(false)
    try {
      await changePassword(currentPassword, next)
      if (profile.data && forced) {
        // The forced change is complete, so the session is free: on to the next gate.
        const freed: Profile = { ...profile.data, passwordChangeRequired: false }
        queryClient.setQueryData(PROFILE_KEY, freed)
        await navigate(landingFor(freed), { replace: true })
        return
      }
      reset()
      setChanged(true)
    } catch (error) {
      if (error instanceof ApiError && error.code === 'PASSWORD_REJECTED') {
        const rule = error.problem.rule
        setError(
          'newPassword',
          { message: isPasswordRule(rule) ? RULE_COPY[rule] : GENERIC_REJECTION },
          { shouldFocus: true },
        )
      } else if (error instanceof ApiError && error.code === 'VALIDATION_FAILED') {
        setError('currentPassword', { message: WRONG_CURRENT }, { shouldFocus: true })
      } else {
        setFailure((error instanceof ApiError && FAILURE_COPY[error.code]) || GENERIC_FAILURE)
      }
    }
  }

  // Back returns to the page the change was opened from (the admin console or the greeting). Opened directly, with
  // no in-app history, it goes to the account's landing page instead.
  const onBack = () => {
    if (location.key !== 'default') {
      void navigate(-1)
    } else if (profile.data) {
      void navigate(landingFor(profile.data))
    }
  }

  const onSignOut = async () => {
    await signOut(queryClient)
    await navigate('/sign-in', { replace: true })
  }

  const newPasswordDescription = ['new-password-bytes', errors.newPassword ? 'new-password-error' : undefined]
    .filter(Boolean)
    .join(' ')

  return (
    <section aria-labelledby="change-password-heading" className="mt-6">
      <h2 id="change-password-heading" className="text-xl font-semibold">
        Change password
      </h2>
      {forced && (
        <p className="mt-2 text-sm">
          You must choose a new password before you can continue. Enter the password you were given, then your new one.
        </p>
      )}
      <form noValidate onSubmit={handleSubmit(onSubmit)} className="mt-4 flex flex-col gap-4">
        <div className="flex flex-col gap-2">
          <Label htmlFor="current-password">Current password</Label>
          <Input
            id="current-password"
            type="password"
            autoComplete="current-password"
            aria-invalid={errors.currentPassword ? true : undefined}
            aria-describedby={errors.currentPassword ? 'current-password-error' : undefined}
            {...register('currentPassword')}
          />
          {errors.currentPassword && (
            <p id="current-password-error" className="text-sm text-destructive">
              {errors.currentPassword.message}
            </p>
          )}
        </div>
        <div className="flex flex-col gap-2">
          <Label htmlFor="new-password">New password</Label>
          <Input
            id="new-password"
            type="password"
            autoComplete="new-password"
            aria-invalid={errors.newPassword ? true : undefined}
            aria-describedby={newPasswordDescription}
            {...register('newPassword')}
          />
          <NewPasswordHints id="new-password" password={newPassword} userInputs={username ? [username] : []} />
          {errors.newPassword && (
            <p id="new-password-error" className="text-sm text-destructive">
              {errors.newPassword.message}
            </p>
          )}
        </div>
        {/* Always rendered, so a screen reader announces the message when it appears (live region). */}
        <p role="alert" className="text-sm text-destructive">
          {failure}
        </p>
        <p role="status" className="text-sm">
          {changed ? 'Your password has been changed. Your other sessions have been signed out.' : ''}
        </p>
        <div className="flex items-center gap-4">
          <Button type="submit" disabled={isSubmitting}>
            Change password
          </Button>
          {forced ? (
            <Button type="button" variant="outline" onClick={onSignOut}>
              Sign out
            </Button>
          ) : (
            <Button type="button" variant="link" className="h-auto p-0 text-sm underline" onClick={onBack}>
              Back
            </Button>
          )}
        </div>
      </form>
    </section>
  )
}
