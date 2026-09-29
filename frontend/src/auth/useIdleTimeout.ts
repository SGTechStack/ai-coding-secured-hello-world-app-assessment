import { useEffect } from 'react'

const ACTIVITY_EVENTS = ['pointerdown', 'keydown', 'wheel', 'touchstart'] as const

/** Last input time shared between tabs: it is a timestamp, not a credential. */
const SHARED_ACTIVITY_KEY = 'hello-auth:last-activity'

const SHARED_WRITE_INTERVAL_MS = 5_000

function readSharedActivity(): number {
  try {
    return Number(window.localStorage.getItem(SHARED_ACTIVITY_KEY)) || 0
  } catch {
    return 0
  }
}

function writeSharedActivity(time: number): void {
  try {
    window.localStorage.setItem(SHARED_ACTIVITY_KEY, String(time))
  } catch {
    // Storage unavailable (e.g. privacy mode): each tab then times out on its own activity.
  }
}

/**
 * Calls `onIdle` once no tab of this app has seen user input for `timeoutMs`. Activity in any
 * tab counts, so an idle background tab does not sign out a tab the user is working in.
 */
export function useIdleTimeout(enabled: boolean, timeoutMs: number, onIdle: () => void): void {
  useEffect(() => {
    if (!enabled) return
    let lastActivity = Date.now()
    let lastSharedWrite = lastActivity
    writeSharedActivity(lastActivity)

    const onActivity = () => {
      lastActivity = Date.now()
      if (lastActivity - lastSharedWrite >= SHARED_WRITE_INTERVAL_MS) {
        writeSharedActivity(lastActivity)
        lastSharedWrite = lastActivity
      }
    }

    let timer = 0
    const check = () => {
      const deadline = Math.max(lastActivity, readSharedActivity()) + timeoutMs
      const remaining = deadline - Date.now()
      if (remaining <= 0) {
        onIdle()
      } else {
        timer = window.setTimeout(check, remaining)
      }
    }

    ACTIVITY_EVENTS.forEach((event) => window.addEventListener(event, onActivity, { passive: true }))
    timer = window.setTimeout(check, timeoutMs)
    return () => {
      window.clearTimeout(timer)
      ACTIVITY_EVENTS.forEach((event) => window.removeEventListener(event, onActivity))
    }
  }, [enabled, timeoutMs, onIdle])
}
