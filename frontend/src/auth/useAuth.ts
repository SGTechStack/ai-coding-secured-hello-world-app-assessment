import { useContext } from 'react'
import { AuthContext, type AuthState } from './authContext'

/**
 * The session, or a thrown error if the component is outside the provider.
 *
 * Throwing rather than returning a default: a default would let a screen render as "logged out" because
 * of a missing provider, which looks like a session bug and is a wiring bug.
 */
export function useAuth(): AuthState {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used inside an AuthProvider')
  }
  return context
}
