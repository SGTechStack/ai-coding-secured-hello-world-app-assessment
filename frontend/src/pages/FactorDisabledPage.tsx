import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Navigate, useNavigate } from 'react-router'
import { Button } from '@/components/ui/button'
import { authorityRoute } from '@/lib/auth/authority'
import { fetchProfile, PROFILE_KEY, signOut } from '@/lib/auth/session'

/** This page's own subject, never a reason to leave it. */
const OWN_CODES = ['FACTOR_DISABLED'] as const

/**
 * `/factor-disabled`: the *terminal factor state* (REJ-049; R-MFA-006). Too many wrong codes disabled this
 * administrator's factor (tier 2, ADR-027): it is still enrolled, but no code can pass, so no challenge is offered.
 * Only another administrator's factor reset restores it. The page offers sign-out and nothing else.
 */
export function FactorDisabledPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const profile = useQuery({ queryKey: PROFILE_KEY, queryFn: fetchProfile })

  const leaving = authorityRoute(profile.error, OWN_CODES)
  if (leaving) {
    return <Navigate to={leaving} replace />
  }

  const onSignOut = async () => {
    await signOut(queryClient)
    await navigate('/sign-in', { replace: true })
  }

  return (
    <section aria-labelledby="factor-disabled-heading" className="mt-6 flex flex-col gap-4">
      <h2 id="factor-disabled-heading" className="text-xl font-semibold">
        Authenticator disabled
      </h2>
      <p className="text-sm">
        Your authenticator was disabled after too many wrong codes, so it can no longer be used to sign in to the admin
        pages. Another administrator must reset it; you can then set up your authenticator again.
      </p>
      <div>
        <Button variant="outline" onClick={onSignOut}>
          Sign out
        </Button>
      </div>
    </section>
  )
}
