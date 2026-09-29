import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Navigate, useNavigate } from 'react-router'
import { INVALID_CODE, TOO_MANY_ATTEMPTS, TotpCodeForm } from '@/components/TotpCodeForm'
import { Button } from '@/components/ui/button'
import { ApiError, type ErrorCode } from '@/lib/api/errors'
import { authorityRoute, useAuthorityFailure } from '@/lib/auth/authority'
import { fetchProfile, PROFILE_KEY, signOut } from '@/lib/auth/session'
import { verifyTotp } from '@/lib/mfa/verification'

const COPY: Partial<Record<ErrorCode, string>> = { TOO_MANY_REQUESTS: TOO_MANY_ATTEMPTS }
const GENERIC_FAILURE = 'The code could not be checked. Try again.'
/** `MISSING_FACTOR` is this page's own subject, never a reason to leave it. */
const OWN_CODES: readonly ErrorCode[] = ['MISSING_FACTOR']

/**
 * `/verify`: the TOTP challenge an enrolled administrator meets after the password (ADR-021; ADR-023). A correct code
 * grants the factor to the session, and the admin surface opens. The server decides: whatever this page believes, the
 * envelope `code` of a refusal says where the session goes.
 */
export function FactorChallengePage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const profile = useQuery({ queryKey: PROFILE_KEY, queryFn: fetchProfile })
  const { redirect, failure, fail, clearFailure } = useAuthorityFailure(OWN_CODES)

  const leaving = redirect ?? authorityRoute(profile.error, OWN_CODES)
  if (leaving) {
    return <Navigate to={leaving} replace />
  }
  // A UX guard only: the server refuses a non-administrator whatever renders here.
  if (profile.data && profile.data.role !== 'ADMIN') {
    return <Navigate to="/hello" replace />
  }

  const onCode = async (code: string) => {
    clearFailure()
    try {
      await verifyTotp(code)
      await queryClient.invalidateQueries({ queryKey: PROFILE_KEY })
      await navigate('/admin/users', { replace: true })
      return undefined
    } catch (error) {
      if (error instanceof ApiError && error.code === 'INVALID_FACTOR') {
        return INVALID_CODE
      }
      fail(error, COPY, GENERIC_FAILURE)
      return undefined
    }
  }

  const onSignOut = async () => {
    await signOut(queryClient)
    await navigate('/sign-in', { replace: true })
  }

  return (
    <section aria-labelledby="challenge-heading" className="mt-6 flex flex-col gap-4">
      <h2 id="challenge-heading" className="text-xl font-semibold">
        TOTP Verification
      </h2>
      <p className="text-sm">Enter the 6-digit code your authenticator app shows for this account.</p>
      <TotpCodeForm submitLabel="Verify" onCode={onCode} autoFocus />
      {/* Always rendered, so a screen reader announces the message when it appears (live region). */}
      <p role="alert" className="text-sm text-destructive">
        {failure}
      </p>
      <div>
        <Button variant="outline" onClick={onSignOut}>
          Sign out
        </Button>
      </div>
    </section>
  )
}
