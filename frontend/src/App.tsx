import type { ReactNode } from 'react'
import { Loader2 } from 'lucide-react'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'

import { AuthProvider, useAuth } from '@/auth/auth-context'
import AdminPage from '@/pages/AdminPage'
import ForgotPasswordPage from '@/pages/ForgotPasswordPage'
import HelloPage from '@/pages/HelloPage'
import LoginPage from '@/pages/LoginPage'
import RegisterPage from '@/pages/RegisterPage'
import ResetPasswordPage from '@/pages/ResetPasswordPage'

/** Full-screen placeholder while the /api/auth/me probe is in flight. */
function SessionRestoring() {
  return (
    <main className="flex min-h-screen items-center justify-center bg-linear-to-b from-primary/10 via-background to-background">
      <p className="flex items-center gap-2 text-sm text-muted-foreground">
        <Loader2 className="size-4 animate-spin" />
        Restoring session…
      </p>
    </main>
  )
}

/** Gate for protected routes — anonymous visitors bounce to /login. */
function RequireAuth({ children }: { children: ReactNode }) {
  const { user, loading } = useAuth()
  if (loading) {
    return <SessionRestoring />
  }
  if (!user) {
    return <Navigate to="/login" replace />
  }
  return children
}

/**
 * Role check layered inside RequireAuth — it only renders once RequireAuth
 * has produced a signed-in principal. Non-admins bounce to /.
 */
function AdminOnly({ children }: { children: ReactNode }) {
  const { user } = useAuth()
  if (user?.role !== 'ADMIN') {
    return <Navigate to="/" replace />
  }
  return children
}

/**
 * Gate for admin routes — composes RequireAuth (loading state + anonymous
 * → /login) and adds the role check (signed-in non-admins → /). The route
 * guard is UX only; the API independently returns 403 to non-ADMIN
 * sessions.
 */
function RequireAdmin({ children }: { children: ReactNode }) {
  return (
    <RequireAuth>
      <AdminOnly>{children}</AdminOnly>
    </RequireAuth>
  )
}

/** Signed-in users have no business on the auth pages. */
function RedirectIfAuthed({ children }: { children: ReactNode }) {
  const { user, loading } = useAuth()
  if (loading) {
    return <SessionRestoring />
  }
  if (user) {
    return <Navigate to="/" replace />
  }
  return children
}

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route
            path="/"
            element={
              <RequireAuth>
                <HelloPage />
              </RequireAuth>
            }
          />
          <Route
            path="/login"
            element={
              <RedirectIfAuthed>
                <LoginPage />
              </RedirectIfAuthed>
            }
          />
          <Route
            path="/register"
            element={
              <RedirectIfAuthed>
                <RegisterPage />
              </RedirectIfAuthed>
            }
          />
          <Route
            path="/forgot-password"
            element={
              <RedirectIfAuthed>
                <ForgotPasswordPage />
              </RedirectIfAuthed>
            }
          />
          {/* The stubbed email's logged link targets this route (?token=…). */}
          <Route
            path="/reset-password"
            element={
              <RedirectIfAuthed>
                <ResetPasswordPage />
              </RedirectIfAuthed>
            }
          />
          <Route
            path="/admin"
            element={
              <RequireAdmin>
                <AdminPage />
              </RequireAdmin>
            }
          />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  )
}
