import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link } from 'react-router'
import { z } from 'zod'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { ApiError } from '@/lib/api/errors'
import { requestPasswordReset } from '@/lib/auth/passwordReset'

export const EMAIL_COPY = 'Enter an email address, such as name@example.com.'
export const TOO_MANY_COPY = 'Too many reset requests for this address. Wait a while, then try again.'
const GENERIC_FAILURE = 'The request failed. Try again.'

const schema = z.object({
  email: z.string().trim().pipe(z.email(EMAIL_COPY)),
})

type ForgotForm = z.infer<typeof schema>

/**
 * Password reset, step one (PRD Story 6). The confirmation is the same whether or not the address has an account, so
 * it never says which.
 */
export function ForgotPasswordPage() {
  const [failure, setFailure] = useState('')
  const [sentTo, setSentTo] = useState('')
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<ForgotForm>({ resolver: zodResolver(schema), defaultValues: { email: '' } })

  const onSubmit = async ({ email }: ForgotForm) => {
    setFailure('')
    try {
      await requestPasswordReset(email)
      setSentTo(email)
    } catch (error) {
      if (error instanceof ApiError && error.code === 'VALIDATION_FAILED') {
        setError('email', { message: EMAIL_COPY }, { shouldFocus: true })
      } else {
        setFailure(error instanceof ApiError && error.code === 'TOO_MANY_REQUESTS' ? TOO_MANY_COPY : GENERIC_FAILURE)
      }
    }
  }

  if (sentTo) {
    return (
      <section aria-labelledby="forgot-heading" className="mt-6">
        <h2 id="forgot-heading" className="text-xl font-semibold">
          Check your email
        </h2>
        <p role="status" className="mt-4">
          If {sentTo} belongs to an account, a reset link is on its way to it. Open the link within 30 minutes to choose
          a new password.
        </p>
        <Link to="/sign-in" className="mt-4 inline-block text-sm underline">
          Sign in
        </Link>
      </section>
    )
  }

  return (
    <section aria-labelledby="forgot-heading" className="mt-6">
      <h2 id="forgot-heading" className="text-xl font-semibold">
        Forgot your password?
      </h2>
      <form noValidate onSubmit={handleSubmit(onSubmit)} className="mt-4 flex flex-col gap-4">
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
            Send reset link
          </Button>
          <Link to="/sign-in" className="text-sm underline">
            Sign in instead
          </Link>
        </div>
      </form>
    </section>
  )
}
