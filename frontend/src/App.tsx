import { useEffect, type ReactNode } from 'react'
import { Navigate, Route, Routes, useNavigate } from 'react-router'
import { onSessionEnded } from './api/client'
import { AuthProvider } from './auth/AuthProvider'
import { useAuth } from './auth/useAuth'
import { HelloPage } from './pages/HelloPage'
import { LoginPage } from './pages/LoginPage'
import { PasswordChangePage } from './pages/PasswordChangePage'
import { RegisterPage } from './pages/RegisterPage'

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
        <Route path="/password-change" element={<LoggedInOnly>{<PasswordChangePage />}</LoggedInOnly>} />
        <Route path="/login" element={<VisitorOnly>{<LoginPage />}</VisitorOnly>} />
        <Route path="/register" element={<RegisterPage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </main>
  )
}

/** Waits for `GET /me`, then shows the page to a logged-in Account holder or sends a Visitor to login. */
function LoggedInOnly({ children }: { children: ReactNode }) {
  const { state } = useAuth()
  if (state.kind === 'loading') return <p>Loading…</p>
  if (state.kind === 'error') return <p role="alert">Something went wrong. Please try again later.</p>
  if (state.kind === 'anonymous') return <Navigate to="/login" replace />
  return children
}

/** The login screen is for Visitors; a logged-in Account holder goes to the hello screen. */
function VisitorOnly({ children }: { children: ReactNode }) {
  const { state } = useAuth()
  return state.kind === 'authenticated' ? <Navigate to="/" replace /> : children
}
