import { useQuery } from '@tanstack/react-query'
import { Navigate } from 'react-router'
import { ApiError } from '@/lib/api/errors'
import { fetchProfile, PROFILE_KEY } from '@/lib/auth/session'

/**
 * The entry route. Where to go is decided by the self-read (belief): a profile means signed in, and
 * `AUTHENTICATION_FAILED` means not.
 */
export function HomePage() {
  const profile = useQuery({ queryKey: PROFILE_KEY, queryFn: fetchProfile })

  if (profile.data) {
    return <Navigate to="/hello" replace />
  }
  if (profile.error instanceof ApiError && profile.error.code === 'AUTHENTICATION_FAILED') {
    return <Navigate to="/sign-in" replace />
  }
  if (profile.error) {
    return (
      <p role="alert" className="mt-4">
        The service is unavailable. Try again later.
      </p>
    )
  }
  return <p className="mt-4 text-muted-foreground">Loading…</p>
}
