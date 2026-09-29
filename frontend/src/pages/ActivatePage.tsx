import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { Link, useLocation } from 'react-router'
import { z } from 'zod'
import { NewPasswordHints } from '@/components/NewPasswordHints'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { ApiError, type ErrorCode } from '@/lib/api/errors'
import { activate, tokenFromFragment } from '@/lib/auth/registration'
import { isPasswordRule, lengthRule, RULE_COPY } from '@/lib/password/policy'

const schema = z.object({
  // Only the two length rules can be checked here; the server runs the whole policy (ADR-005).
  password: z.string().superRefine((value, context) => {
    const rule = lengthRule(value)
    if (rule) {
      context.addIssue({ code: 'custom', message: RULE_COPY[rule] })
    }
  }),
})

type ActivationForm = z.infer<typeof schema>

export const INVALID_LINK =
  'This activation link is not valid. It may have expired, been used already or been replaced by a newer one. Register again to get a new link.'
const FAILURE_COPY: Partial<Record<ErrorCode, string>> = {
  RESET_TOKEN_INVALID: INVALID_LINK,
  TOO_MANY_REQUESTS: 'Too many attempts. Wait a moment, then try again.',
}
const GENERIC_FAILURE = 'Activation failed. Try again.'
const GENERIC_REJECTION = 'The password was not accepted. Choose another.'

/**
 * Self-registration, step two (ADR-032): the activation link opens this page with its token in the fragment, and the
 * first password is set here. Only whoever holds the link can set it. It signs nobody in: the user signs in next.
 */
export function ActivatePage() {
  const { hash } = useLocation()
  const [token] = useState(() => tokenFromFragment(hash))
  const [failure, setFailure] = useState('')
  const [activated, setActivated] = useState(false)
  const {
    register,
    handleSubmit,
    setError,
    control,
    formState: { errors, isSubmitting },
  } = useForm<ActivationForm>({ resolver: zodResolver(schema), defaultValues: { password: '' } })
  const password = useWatch({ control, name: 'password' })

  const onSubmit = async ({ password: chosen }: ActivationForm) => {
    setFailure('')
    try {
      await activate(token, chosen)
      setActivated(true)
    } catch (error) {
      if (error instanceof ApiError && error.code === 'PASSWORD_REJECTED') {
        const rule = error.problem.rule
        setError(
          'password',
          { message: isPasswordRule(rule) ? RULE_COPY[rule] : GENERIC_REJECTION },
          { shouldFocus: true },
        )
      } else {
        setFailure((error instanceof ApiError && FAILURE_COPY[error.code]) || GENERIC_FAILURE)
      }
    }
  }

  if (activated) {
    return (
      <section aria-labelledby="activate-heading" className="mt-6">
        <h2 id="activate-heading" className="text-xl font-semibold">
          Account activated
        </h2>
        <p role="status" className="mt-4">
          Your password is set. You can now sign in.
        </p>
        <Link to="/sign-in" className="mt-4 inline-block text-sm underline">
          Sign in
        </Link>
      </section>
    )
  }

  if (!token) {
    return (
      <section aria-labelledby="activate-heading" className="mt-6">
        <h2 id="activate-heading" className="text-xl font-semibold">
          Activate your account
        </h2>
        <p role="alert" className="mt-4">
          {INVALID_LINK}
        </p>
        <Link to="/register" className="mt-4 inline-block text-sm underline">
          Register
        </Link>
      </section>
    )
  }

  const passwordDescription = ['password-bytes', errors.password ? 'password-error' : undefined]
    .filter(Boolean)
    .join(' ')

  return (
    <section aria-labelledby="activate-heading" className="mt-6">
      <h2 id="activate-heading" className="text-xl font-semibold">
        Activate your account
      </h2>
      <form noValidate onSubmit={handleSubmit(onSubmit)} className="mt-4 flex flex-col gap-4">
        <div className="flex flex-col gap-2">
          <Label htmlFor="password">Choose a password</Label>
          <Input
            id="password"
            type="password"
            autoComplete="new-password"
            aria-invalid={errors.password ? true : undefined}
            aria-describedby={passwordDescription}
            {...register('password')}
          />
          <NewPasswordHints id="password" password={password} userInputs={[]} />
          {errors.password && (
            <p id="password-error" className="text-sm text-destructive">
              {errors.password.message}
            </p>
          )}
        </div>
        {/* Always rendered, so a screen reader announces the message when it appears (live region). */}
        <p role="alert" className="text-sm text-destructive">
          {failure}
        </p>
        <Button type="submit" disabled={isSubmitting}>
          Activate
        </Button>
      </form>
    </section>
  )
}
