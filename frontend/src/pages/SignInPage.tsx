import { zodResolver } from '@hookform/resolvers/zod'
import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { useNavigate } from 'react-router'
import { z } from 'zod'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { ApiError, type ErrorCode } from '@/lib/api/errors'
import { PROFILE_KEY, signIn } from '@/lib/auth/session'

const schema = z.object({
  username: z.string().trim().min(1, 'Enter your username.'),
  password: z.string().min(1, 'Enter your password.'),
})

type Credentials = z.infer<typeof schema>

/** What a refused sign-in tells the user. Every password-axis failure is one code, so one message (ADR-033). */
const FAILURE_COPY: Partial<Record<ErrorCode, string>> = {
  AUTHENTICATION_FAILED: 'The username or password is not correct.',
  TOO_MANY_REQUESTS: 'Too many attempts. Wait a moment, then try again.',
}
const GENERIC_FAILURE = 'Sign-in failed. Try again.'

export function SignInPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [failure, setFailure] = useState('')
  const {
    register,
    handleSubmit,
    setFocus,
    resetField,
    formState: { errors, isSubmitting },
  } = useForm<Credentials>({ resolver: zodResolver(schema), defaultValues: { username: '', password: '' } })

  const onSubmit = async ({ username, password }: Credentials) => {
    setFailure('')
    try {
      const profile = await signIn(username, password)
      queryClient.setQueryData(PROFILE_KEY, profile)
      await navigate('/hello', { replace: true })
    } catch (error) {
      setFailure((error instanceof ApiError && FAILURE_COPY[error.code]) || GENERIC_FAILURE)
      resetField('password')
      setFocus('password')
    }
  }

  return (
    <section aria-labelledby="sign-in-heading" className="mt-6">
      <h2 id="sign-in-heading" className="text-xl font-semibold">
        Sign in
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
          <Label htmlFor="password">Password</Label>
          <Input
            id="password"
            type="password"
            autoComplete="current-password"
            aria-invalid={errors.password ? true : undefined}
            aria-describedby={errors.password ? 'password-error' : undefined}
            {...register('password')}
          />
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
          Sign in
        </Button>
      </form>
    </section>
  )
}
