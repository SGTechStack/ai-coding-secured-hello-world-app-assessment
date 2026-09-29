import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link } from 'react-router'
import { z } from 'zod'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { ApiError, type ErrorCode } from '@/lib/api/errors'
import { register as registerAccount } from '@/lib/auth/registration'

/** Mirrors the server's username rule (ADR-045; REJ-027): it rejects rather than changes what was typed. */
export const USERNAME_PATTERN = /^[a-z0-9._-]{3,32}$/

export const USERNAME_RULE_COPY =
  'Use 3 to 32 characters: lowercase letters, digits, dots, hyphens and underscores. No @ or spaces.'
export const USERNAME_UNAVAILABLE_COPY = 'That username is not available. Choose another.'

const schema = z.object({
  username: z.string().regex(USERNAME_PATTERN, USERNAME_RULE_COPY),
  email: z.string().trim().pipe(z.email('Enter an email address, such as name@example.com.')),
})

type RegistrationForm = z.infer<typeof schema>

const FAILURE_COPY: Partial<Record<ErrorCode, string>> = {
  TOO_MANY_REQUESTS: 'Too many attempts. Wait a moment, then try again.',
}
const GENERIC_FAILURE = 'Registration failed. Try again.'
const NOT_ACCEPTED = 'The username or email address was not accepted. Check them and try again.'

/**
 * Self-registration, step one (ADR-032). No password is asked for here: it is set from the activation link. The
 * confirmation is the same whether or not the address already has an account, so it never says which.
 */
export function RegisterPage() {
  const [failure, setFailure] = useState('')
  const [sentTo, setSentTo] = useState('')
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<RegistrationForm>({ resolver: zodResolver(schema), defaultValues: { username: '', email: '' } })

  const onSubmit = async ({ username, email }: RegistrationForm) => {
    setFailure('')
    try {
      await registerAccount(username, email)
      setSentTo(email)
    } catch (error) {
      if (error instanceof ApiError && error.code === 'VALIDATION_FAILED') {
        const unavailable = error.problem.rule === 'USERNAME_UNAVAILABLE'
        setError('username', { message: unavailable ? USERNAME_UNAVAILABLE_COPY : NOT_ACCEPTED }, { shouldFocus: true })
      } else {
        setFailure((error instanceof ApiError && FAILURE_COPY[error.code]) || GENERIC_FAILURE)
      }
    }
  }

  if (sentTo) {
    return (
      <section aria-labelledby="register-heading" className="mt-6">
        <h2 id="register-heading" className="text-xl font-semibold">
          Check your email
        </h2>
        <p role="status" className="mt-4">
          If {sentTo} can be registered, an activation link is on its way to it. Open the link within 24 hours to set
          your password.
        </p>
        <Link to="/sign-in" className="mt-4 inline-block text-sm underline">
          Sign in
        </Link>
      </section>
    )
  }

  return (
    <section aria-labelledby="register-heading" className="mt-6">
      <h2 id="register-heading" className="text-xl font-semibold">
        Register
      </h2>
      <form noValidate onSubmit={handleSubmit(onSubmit)} className="mt-4 flex flex-col gap-4">
        <div className="flex flex-col gap-2">
          <Label htmlFor="username">Username</Label>
          <Input
            id="username"
            autoComplete="username"
            aria-invalid={errors.username ? true : undefined}
            aria-describedby={errors.username ? 'username-error' : undefined}
            {...register('username')}
          />
          {errors.username && (
            <p id="username-error" className="text-sm text-destructive">
              {errors.username.message}
            </p>
          )}
        </div>
        <div className="flex flex-col gap-2">
          <Label htmlFor="email">Email address</Label>
          <Input
            id="email"
            type="email"
            autoComplete="email"
            aria-invalid={errors.email ? true : undefined}
            aria-describedby={errors.email ? 'email-error' : undefined}
            {...register('email')}
          />
          {errors.email && (
            <p id="email-error" className="text-sm text-destructive">
              {errors.email.message}
            </p>
          )}
        </div>
        {/* Always rendered, so a screen reader announces the message when it appears (live region). */}
        <p role="alert" className="text-sm text-destructive">
          {failure}
        </p>
        <div className="flex items-center gap-4">
          <Button type="submit" disabled={isSubmitting}>
            Register
          </Button>
          <Link to="/sign-in" className="text-sm underline">
            Sign in instead
          </Link>
        </div>
      </form>
    </section>
  )
}
