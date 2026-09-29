import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { Navigate, useNavigate } from 'react-router'
import { INVALID_CODE, TOO_MANY_ATTEMPTS, TotpCodeForm } from '@/components/TotpCodeForm'
import { Button } from '@/components/ui/button'
import { ApiError, type ErrorCode } from '@/lib/api/errors'
import { authorityRoute, useAuthorityFailure } from '@/lib/auth/authority'
import { FACTOR_DISABLED_ROUTE, fetchProfile, PROFILE_KEY, signOut } from '@/lib/auth/session'
import { verifyTotp } from '@/lib/mfa/verification'

const COPY: Partial<Record<ErrorCode, string>> = { TOO_MANY_REQUESTS: TOO_MANY_ATTEMPTS }
const GENERIC_FAILURE = 'The code could not be checked. Try again.'
const UNAVAILABLE = 'The service is unavailable. Try again later.'

/** The copy for a throttle whose `Retry-After` is known: how long until a code can be sent again. */
function lockedCopy(seconds: number): string {
  const [count, unit] = seconds < 60 ? [seconds, 'second'] : [Math.ceil(seconds / 60), 'minute']
  return `Too many attempts. Try again in ${count} ${unit}${count === 1 ? '' : 's'}.`
}

/** A throttle with a known end: its copy, and how long Verify stays disabled. */
interface Lock {
  message: string
  milliseconds: number
}

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
  const [lock, setLock] = useState<Lock>()

  // Verify stays disabled until the server's Retry-After has elapsed, then the lock's copy clears.
  useEffect(() => {
    if (!lock) {
      return undefined
    }
    const timer = setTimeout(() => setLock(undefined), lock.milliseconds)
    return () => clearTimeout(timer)
  }, [lock])

  const leaving = redirect ?? authorityRoute(profile.error, OWN_CODES)
  if (leaving) {
    return <Navigate to={leaving} replace />
  }
  // A UX guard only: the server refuses a non-administrator whatever renders here.
  if (profile.data && profile.data.role !== 'ADMIN') {
    return <Navigate to="/hello" replace />
  }
  // The terminal factor state: no code can pass, so the prompt is never offered, not even while the self-read loads.
  if (profile.data?.factors.rebindRequired) {
    return <Navigate to={FACTOR_DISABLED_ROUTE} replace />
  }
  if (profile.error) {
    return (
      <div className="mt-4 flex flex-col items-start gap-2">
        <p role="alert">{UNAVAILABLE}</p>
        <Button variant="outline" onClick={() => void profile.refetch()}>
          Try again
        </Button>
      </div>
    )
  }
  if (!profile.data) {
    return <p className="mt-4 text-muted-foreground">Loading…</p>
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
      // A factor lock or a source throttle: say how long, and hold Verify until then (ADR-027; ADR-033).
      if (error instanceof ApiError && error.code === 'TOO_MANY_REQUESTS' && error.retryAfterSeconds) {
        setLock({ message: lockedCopy(error.retryAfterSeconds), milliseconds: error.retryAfterSeconds * 1000 })
        return undefined
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
      <TotpCodeForm submitLabel="Verify" onCode={onCode} autoFocus disabled={lock !== undefined} />
      {/* Always rendered, so a screen reader announces the message when it appears (live region). */}
      <p role="alert" className="text-sm text-destructive">
        {lock?.message ?? failure}
      </p>
      <div>
        <Button variant="outline" onClick={onSignOut}>
          Sign out
        </Button>
      </div>
    </section>
  )
}
