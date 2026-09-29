import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { Link, useLocation } from 'react-router'
import { z } from 'zod'
import { NewPasswordHints } from '@/components/NewPasswordHints'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { ApiError } from '@/lib/api/errors'
import { tokenFromFragment } from '@/lib/auth/registration'
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

type PasswordForm = z.infer<typeof schema>

const TOO_MANY = 'Too many attempts. Wait a moment, then try again.'
const GENERIC_REJECTION = 'The password was not accepted. Choose another.'

/** What differs between the pages that set a password from an emailed link. */
export interface LinkPasswordCopy {
  heading: string
  submitLabel: string
  doneHeading: string
  /** Shown for a missing, invalid, used or expired link, which never says which. */
  invalidLink: string
  /** Where a user with a bad link gets a new one. */
  newLink: { to: string; label: string }
  genericFailure: string
}

interface Props {
  copy: LinkPasswordCopy
  /** Redeems the link's token with the chosen password. */
  submit: (token: string, password: string) => Promise<void>
}

/**
 * Sets a password from an emailed link: an activation or a password-reset link opens its page with the token in the
 * fragment, which no server ever sees. Only whoever holds the link can set the password, and nobody is signed in: the
 * user signs in next.
 */
export function LinkPasswordForm({ copy, submit }: Props) {
  const { hash } = useLocation()
  const [token] = useState(() => tokenFromFragment(hash))
  const [failure, setFailure] = useState('')
  const [done, setDone] = useState(false)
  const {
    register,
    handleSubmit,
    setError,
    control,
    formState: { errors, isSubmitting },
  } = useForm<PasswordForm>({ resolver: zodResolver(schema), defaultValues: { password: '' } })
  const password = useWatch({ control, name: 'password' })

  const onSubmit = async ({ password: chosen }: PasswordForm) => {
    setFailure('')
    try {
      await submit(token, chosen)
      setDone(true)
    } catch (error) {
      if (error instanceof ApiError && error.code === 'PASSWORD_REJECTED') {
        const rule = error.problem.rule
        setError(
          'password',
          { message: isPasswordRule(rule) ? RULE_COPY[rule] : GENERIC_REJECTION },
          { shouldFocus: true },
        )
      } else if (error instanceof ApiError && error.code === 'RESET_TOKEN_INVALID') {
        setFailure(copy.invalidLink)
      } else if (error instanceof ApiError && error.code === 'TOO_MANY_REQUESTS') {
        setFailure(TOO_MANY)
      } else {
        setFailure(copy.genericFailure)
      }
    }
  }

  if (done) {
    return (
      <section aria-labelledby="link-password-heading" className="mt-6">
        <h2 id="link-password-heading" className="text-xl font-semibold">
          {copy.doneHeading}
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
      <section aria-labelledby="link-password-heading" className="mt-6">
        <h2 id="link-password-heading" className="text-xl font-semibold">
          {copy.heading}
        </h2>
        <p role="alert" className="mt-4">
          {copy.invalidLink}
        </p>
        <Link to={copy.newLink.to} className="mt-4 inline-block text-sm underline">
          {copy.newLink.label}
        </Link>
      </section>
    )
  }

  const passwordDescription = ['password-bytes', errors.password ? 'password-error' : undefined]
    .filter(Boolean)
    .join(' ')

  return (
    <section aria-labelledby="link-password-heading" className="mt-6">
      <h2 id="link-password-heading" className="text-xl font-semibold">
        {copy.heading}
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
          {copy.submitLabel}
        </Button>
      </form>
    </section>
  )
}
