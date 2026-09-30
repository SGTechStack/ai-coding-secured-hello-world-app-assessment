import { useEffect, type ReactNode } from 'react'
import { Navigate, Route, Routes, useNavigate } from 'react-router'
import { onSessionEnded } from './api/client'
import { AuthProvider } from './auth/AuthProvider'
import { useAuth } from './auth/useAuth'
import { AdminUsersPage } from './pages/AdminUsersPage'
import { ForgotPasswordPage } from './pages/ForgotPasswordPage'
import { HelloPage } from './pages/HelloPage'
import { LoginPage } from './pages/LoginPage'
import { PasswordChangePage } from './pages/PasswordChangePage'
import { RegisterPage } from './pages/RegisterPage'
import { ResetPasswordPage } from './pages/ResetPasswordPage'

export default function App() {
  return (
    <AuthProvider>
      <AppRoutes />
    </AuthProvider>
  )
}

function AppRoutes() {
  const navigate = useNavigate()

  // Global 401 handling: an ended Session is a normal state, so go to login without showing an error.
  useEffect(() => onSessionEnded(() => navigate('/login', { replace: true })), [navigate])

  return (
    <main className="app">
      <Routes>
        <Route path="/" element={<LoggedInOnly>{<HelloPage />}</LoggedInOnly>} />
        <Route
          path="/password-change"
          element={
            <LoggedInOnly duringRequiredPasswordChange>
              <PasswordChangePage />
            </LoggedInOnly>
          }
        />
        <Route path="/admin/users" element={<AdminOnly>{<AdminUsersPage />}</AdminOnly>} />
        <Route path="/login" element={<VisitorOnly>{<LoginPage />}</VisitorOnly>} />
        <Route path="/register" element={<VisitorOnly>{<RegisterPage />}</VisitorOnly>} />
        <Route path="/forgot-password" element={<VisitorOnly>{<ForgotPasswordPage />}</VisitorOnly>} />
        <Route path="/reset-password" element={<VisitorOnly>{<ResetPasswordPage />}</VisitorOnly>} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </main>
  )
}

/**
 * Waits for `GET /me`, then shows the page to a logged-in Account holder or sends a Visitor to login.
 * While the Account's password must be changed, only the Password Change screen (which passes
 * `duringRequiredPasswordChange`) and the logout button on it are reachable: every other screen sends
 * the holder there, matching the API, which refuses everything else (story 108).
 */
function LoggedInOnly({
  children,
  duringRequiredPasswordChange = false,
}: {
  children: ReactNode
  duringRequiredPasswordChange?: boolean
}) {
  const { state } = useAuth()
  if (state.kind === 'loading') return <p>Loading…</p>
  if (state.kind === 'error') return <p role="alert">Something went wrong. Please try again later.</p>
  if (state.kind === 'anonymous') return <Navigate to="/login" replace />
  if (state.account.passwordChangeRequired && !duringRequiredPasswordChange)
    return <Navigate to="/password-change" replace />
  return children
}

/**
 * Admin screens are hidden from Users, who go to the hello screen. This is interface only: the API
 * refuses every admin request from a User on its own.
 */
function AdminOnly({ children }: { children: ReactNode }) {
  const { state } = useAuth()
  return (
    <LoggedInOnly>
      {state.kind === 'authenticated' && state.account.role === 'ADMIN' ? children : <Navigate to="/" replace />}
    </LoggedInOnly>
  )
}

/**
 * The Visitor screens — login, register, forgot password and reset password. A logged-in Account
 * holder goes to the hello screen, which sends them on to Password Change while one is required
 * (story 108). None of these screens calls an endpoint an authenticated Session can usefully use, so
 * this one guard covers both cases; a logged-out holder still reaches the reset flow, which is how a
 * completed reset clears the requirement.
 */
function VisitorOnly({ children }: { children: ReactNode }) {
  const { state } = useAuth()
  return state.kind === 'authenticated' ? <Navigate to="/" replace /> : children
}
