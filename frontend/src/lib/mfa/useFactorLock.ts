import { useEffect, useState } from 'react'
import { ApiError } from '@/lib/api/errors'

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

/**
 * A factor lock or a source throttle on a code route (ADR-027; ADR-033): `holdFor` takes a refusal and, when it is a
 * 429 with a `Retry-After`, holds Verify (`locked`) with copy saying how long (`message`) until it elapses.
 */
export function useFactorLock() {
  const [lock, setLock] = useState<Lock>()

  // Verify stays disabled until the server's Retry-After has elapsed, then the lock's copy clears.
  useEffect(() => {
    if (!lock) {
      return undefined
    }
    const timer = setTimeout(() => setLock(undefined), lock.milliseconds)
    return () => clearTimeout(timer)
  }, [lock])

  /** Whether `error` is a throttle with a known end, which is now held. */
  const holdFor = (error: unknown): boolean => {
    if (error instanceof ApiError && error.code === 'TOO_MANY_REQUESTS' && error.retryAfterSeconds) {
      setLock({ message: lockedCopy(error.retryAfterSeconds), milliseconds: error.retryAfterSeconds * 1000 })
      return true
    }
    return false
  }

  return { locked: lock !== undefined, message: lock?.message, holdFor }
}
