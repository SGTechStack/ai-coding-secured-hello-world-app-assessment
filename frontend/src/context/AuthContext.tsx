import { createContext, useContext, useState, type ReactNode } from 'react'

interface AuthState {
  username: string | null
  role: string | null
  forcePasswordChange: boolean
}

interface AuthContextValue extends AuthState {
  setSession: (state: AuthState) => void
  clearSession: () => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

const EMPTY_STATE: AuthState = { username: null, role: null, forcePasswordChange: false }

export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>(EMPTY_STATE)

  return (
    <AuthContext.Provider
      value={{
        ...state,
        setSession: setState,
        clearSession: () => setState(EMPTY_STATE),
      }}
    >
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider')
  }
  return context
}
