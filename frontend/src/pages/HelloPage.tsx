import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, Navigate, useNavigate } from 'react-router'
import { Button } from '@/components/ui/button'
import { ApiError } from '@/lib/api/errors'
import { fetchGreeting, signOut } from '@/lib/auth/session'

/** The signed-in page: the server's greeting, a link to change the password, and sign-out. */
export function HelloPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const greeting = useQuery({ queryKey: ['hello'], queryFn: fetchGreeting })

  // Authority: whatever the client believed, the server's code decides that the session is over.
  if (greeting.error instanceof ApiError && greeting.error.code === 'AUTHENTICATION_FAILED') {
    return <Navigate to="/sign-in" replace />
  }
  // The first gate, by authority: a forced-change session is refused everything but the allowlist (ADR-046).
  if (greeting.error instanceof ApiError && greeting.error.code === 'PASSWORD_CHANGE_REQUIRED') {
    return <Navigate to="/change-password" replace />
  }

  const onSignOut = async () => {
    await signOut(queryClient)
    await navigate('/sign-in', { replace: true })
  }

  return (
    <section className="mt-6 flex flex-col items-start gap-4">
      {greeting.data && <h2 className="text-xl font-semibold">{greeting.data.message}</h2>}
      {greeting.isPending && <p className="text-muted-foreground">Loading…</p>}
      {greeting.error && (
        <p role="alert" className="text-sm text-destructive">
          The greeting could not be loaded.
        </p>
      )}
      <Link to="/change-password" className="text-sm underline">
        Change password
      </Link>
      <Button variant="outline" onClick={onSignOut}>
        Sign out
      </Button>
    </section>
  )
}
