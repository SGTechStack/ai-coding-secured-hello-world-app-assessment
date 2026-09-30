import { zodResolver } from '@hookform/resolvers/zod'
import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, Navigate } from 'react-router'
import { z } from 'zod'
import { OneTimeToken } from '@/components/OneTimeToken'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { ApiError, type ErrorCode } from '@/lib/api/errors'
import { inviteUser, oneTimeLink } from '@/lib/admin/credentials'
import { ADMIN_USERS_KEY } from '@/lib/admin/users'
import { useAuthorityFailure } from '@/lib/auth/authority'
import { USERNAME_PATTERN, USERNAME_RULE_COPY } from '@/pages/RegisterPage'

const schema = z.object({
  username: z.string().regex(USERNAME_PATTERN, USERNAME_RULE_COPY),
  email: z.string().trim().pipe(z.email('Enter an email address, such as name@example.com.')),
  role: z.enum(['USER', 'ADMIN']),
})

type InviteForm = z.infer<typeof schema>

const USER_EXISTS_COPY = 'An account already has that username or email address, or once had it. Choose another.'
const NOT_ACCEPTED = 'The username or email address was not accepted. Check them and try again.'
const INVITE_FAILED = 'The invitation could not be created. Try again.'
const INVITE_COPY: Partial<Record<ErrorCode, string>> = {}

/**
 * `/admin/users/invite`: an administrator creates an account by invite (ADR-006) and is shown its activation link
 * once, to pass on. No password is asked for: the user sets their own when they open the link.
 */
export function AdminInvitePage() {
  const queryClient = useQueryClient()
  const [invited, setInvited] = useState<{ username: string; token: string }>()
  const { redirect, failure, fail, clearFailure } = useAuthorityFailure()
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<InviteForm>({ resolver: zodResolver(schema), defaultValues: { username: '', email: '', role: 'USER' } })

  if (redirect) {
    return <Navigate to={redirect} replace />
  }

  const onSubmit = async ({ username, email, role }: InviteForm) => {
    clearFailure()
    try {
      const { token } = await inviteUser(username, email, role)
      void queryClient.invalidateQueries({ queryKey: ADMIN_USERS_KEY, exact: true })
      setInvited({ username, token })
    } catch (error) {
      if (error instanceof ApiError && (error.code === 'USER_EXISTS' || error.code === 'VALIDATION_FAILED')) {
        const message = error.code === 'USER_EXISTS' ? USER_EXISTS_COPY : NOT_ACCEPTED
        setError('username', { message }, { shouldFocus: true })
      } else {
        fail(error, INVITE_COPY, INVITE_FAILED)
      }
    }
  }

  return (
    <section aria-labelledby="invite-heading" className="mt-6 flex flex-col gap-4">
      <h2 id="invite-heading" className="text-xl font-semibold">
        Invite a user
      </h2>
      {invited ? (
        <OneTimeToken
          heading={`Invitation for ${invited.username}`}
          token={invited.token}
          link={oneTimeLink('activation', invited.token)}
        />
      ) : (
        <form noValidate onSubmit={handleSubmit(onSubmit)} className="flex flex-col gap-4">
          <div className="flex flex-col gap-2">
            <Label htmlFor="username">Username</Label>
            <Input
              id="username"
              autoComplete="off"
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
              autoComplete="off"
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
          <div className="flex flex-col gap-2">
            <Label htmlFor="role">Role</Label>
            <select id="role" className="h-9 w-fit rounded-md border bg-transparent px-3 text-sm" {...register('role')}>
              <option value="USER">User</option>
              <option value="ADMIN">Administrator</option>
            </select>
          </div>
          <p role="alert" className="text-sm text-destructive">
            {failure}
          </p>
          <div>
            <Button type="submit" disabled={isSubmitting}>
              Create invitation
            </Button>
          </div>
        </form>
      )}
      <Link to="/admin/users" className="text-sm underline">
        Back to the user list
      </Link>
    </section>
  )
}
