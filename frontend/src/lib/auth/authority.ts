import { useState } from 'react'
import { ApiError, type ErrorCode } from '@/lib/api/errors'
import { StepUpCancelledError } from '@/lib/api/stepUp'
import { FACTOR_DISABLED_ROUTE } from '@/lib/auth/session'

/**
 * Where an envelope `code` says the session must go, whatever the client believed (authority over belief): signed
 * out, a forced change, no enrolment, no current factor, a disabled factor, or not an administrator. Undefined for
 * anything else.
 */
const AUTHORITY_ROUTES: Partial<Record<ErrorCode, string>> = {
  AUTHENTICATION_FAILED: '/sign-in',
  PASSWORD_CHANGE_REQUIRED: '/change-password',
  FACTOR_ENROLMENT_REQUIRED: '/settings/mfa',
  MISSING_FACTOR: '/verify',
  FACTOR_DISABLED: FACTOR_DISABLED_ROUTE,
  ACCESS_DENIED: '/hello',
}

/**
 * The route `error`'s code sends the session to. A page names only the codes it handles itself in `except`, so a code
 * added here reaches every page.
 */
export function authorityRoute(error: unknown, except: readonly ErrorCode[] = []): string | undefined {
  return error instanceof ApiError && !except.includes(error.code) ? AUTHORITY_ROUTES[error.code] : undefined
}

/** The copy for a change whose step-up challenge was dismissed: it was not sent again. */
export const STEP_UP_CANCELLED = 'Verification was cancelled, so nothing was changed.'

/**
 * A page's failure handling: `fail` follows a code that moves the session elsewhere (into `redirect`), and otherwise
 * shows the page's copy for the code, or `generic`, in `failure`. A dismissed step-up says nothing was changed.
 */
export function useAuthorityFailure(except: readonly ErrorCode[] = []) {
  const [redirect, setRedirect] = useState<string>()
  const [failure, setFailure] = useState('')

  const fail = (error: unknown, copy: Partial<Record<ErrorCode, string>>, generic: string) => {
    const route = authorityRoute(error, except)
    if (route) {
      setRedirect(route)
    } else if (error instanceof StepUpCancelledError) {
      setFailure(STEP_UP_CANCELLED)
    } else {
      setFailure((error instanceof ApiError && copy[error.code]) || generic)
    }
  }

  return { redirect, failure, fail, clearFailure: () => setFailure('') }
}
