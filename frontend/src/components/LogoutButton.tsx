import { useState } from 'react'
import { logout } from '../api/auth'
import { useAuth } from '../auth/useAuth'

const GENERIC_ERROR = 'Something went wrong. Please try again later.'

/**
 * Logs out and lets the route guard send the now-anonymous caller to the login screen. Shared by the
 * hello screen and the Password Change screen, which is the only other screen a holder whose password
 * must be changed can reach.
 */
export function LogoutButton() {
  const auth = useAuth()
  const [failed, setFailed] = useState(false)

  async function logOut() {
    setFailed(false)
    try {
      if (await logout()) {
        auth.loggedOut()
        return
      }
    } catch {
      // Falls through to the error message.
    }
    setFailed(true)
  }

  return (
    <>
      {failed && <p role="alert">{GENERIC_ERROR}</p>}
      <button type="button" onClick={logOut}>
        Log out
      </button>
    </>
  )
}
