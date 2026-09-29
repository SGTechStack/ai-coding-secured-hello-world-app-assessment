import { zodResolver } from '@hookform/resolvers/zod'
import { useQuery } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { Link, Navigate } from 'react-router'
import { z } from 'zod'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { ApiError, type ErrorCode } from '@/lib/api/errors'
import { changePassword, fetchProfile, PROFILE_KEY } from '@/lib/auth/session'
import { isPasswordRule, lengthRule, MAX_BYTES, RULE_COPY, utf8Bytes } from '@/lib/password/policy'
import { estimateStrength, type StrengthScore } from '@/lib/password/strength'

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

const STRENGTH_LABELS: Readonly<Record<StrengthScore, string>> = {
  0: 'Very weak',
  1: 'Weak',
  2: 'Fair',
  3: 'Strong',
  4: 'Very strong',
}

const WRONG_CURRENT = 'The current password is not correct.'
const FAILURE_COPY: Partial<Record<ErrorCode, string>> = {
  TOO_MANY_REQUESTS: 'Too many attempts. Wait a moment, then try again.',
}
const GENERIC_FAILURE = 'The password could not be changed. Try again.'
const GENERIC_REJECTION = 'The new password was not accepted. Choose another.'

/**
 * Self-service password change (ADR-008). The current password is always required. Paste and password managers work
 * on both fields (R-FE-001). The strength meter is indicative only; the server decides (ADR-005).
 */
export function ChangePasswordPage() {
  const profile = useQuery({ queryKey: PROFILE_KEY, queryFn: fetchProfile })
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
  const [estimate, setEstimate] = useState<{ password: string; score: StrengthScore }>()

  useEffect(() => {
    if (!newPassword) {
      return
    }
    let current = true
    void estimateStrength(newPassword, username ? [username] : []).then((score) => {
      if (current) {
        setEstimate({ password: newPassword, score })
      }
    })
    return () => {
      current = false
    }
  }, [newPassword, username])

  // Authority: whatever the client believed, the server's code decides that the session is over.
  if (profile.error instanceof ApiError && profile.error.code === 'AUTHENTICATION_FAILED') {
    return <Navigate to="/sign-in" replace />
  }

  const onSubmit = async ({ currentPassword, newPassword: next }: PasswordChangeForm) => {
    setFailure('')
    setChanged(false)
    try {
      await changePassword(currentPassword, next)
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

  const score = newPassword && estimate?.password === newPassword ? estimate.score : undefined
  const newPasswordDescription = ['new-password-bytes', errors.newPassword ? 'new-password-error' : undefined]
    .filter(Boolean)
    .join(' ')

  return (
    <section aria-labelledby="change-password-heading" className="mt-6">
      <h2 id="change-password-heading" className="text-xl font-semibold">
        Change password
      </h2>
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
          <p id="new-password-bytes" className="text-sm text-muted-foreground">
            {utf8Bytes(newPassword)} of {MAX_BYTES} bytes
          </p>
          <div className="flex items-center gap-2 text-sm">
            <label htmlFor="new-password-strength">Strength (indicative)</label>
            <meter id="new-password-strength" min={0} max={4} low={2} high={3} optimum={4} value={score ?? 0} />
            <span aria-live="polite">{score === undefined ? '' : STRENGTH_LABELS[score]}</span>
          </div>
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
          <Link to="/hello" className="text-sm underline">
            Back
          </Link>
        </div>
      </form>
    </section>
  )
}
