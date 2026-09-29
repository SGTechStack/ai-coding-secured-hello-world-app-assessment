import { useQuery } from '@tanstack/react-query'
import { Navigate, Outlet } from 'react-router'
import { authorityRoute } from '@/lib/auth/authority'
import { fetchProfile, landingFor, PROFILE_KEY } from '@/lib/auth/session'

/**
 * The admin routes' guard, in gate order (spec, Frontend): a session that the self-read says is not a verified
 * administrator is sent to where it belongs before anything renders. UX only: the server refuses the admin data
 * whatever this decides, and the pages follow the server's code (T-FE-002).
 */
export function AdminGate() {
  const profile = useQuery({ queryKey: PROFILE_KEY, queryFn: fetchProfile })

  const leaving = authorityRoute(profile.error, ['AUTHENTICATION_FAILED'])
  if (leaving) {
    return <Navigate to={leaving} replace />
  }
  if (profile.error) {
    return (
      <p role="alert" className="mt-4">
        The service is unavailable. Try again later.
      </p>
    )
  }
  if (!profile.data) {
    return <p className="mt-4 text-muted-foreground">Loading…</p>
  }
  const landing = landingFor(profile.data)
  return landing === '/admin/users' ? <Outlet /> : <Navigate to={landing} replace />
}
