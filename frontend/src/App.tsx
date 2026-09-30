import type { ReactNode } from 'react'
import { Link, Navigate, Route, Routes } from 'react-router'
import { RequireAnonymous, RequireSession, RequireUserManager } from './auth/guards'
import { useAuth } from './auth/useAuth'
import { ChangePasswordPage } from './routes/ChangePasswordPage'
import { ForgotPasswordPage } from './routes/ForgotPasswordPage'
import { HelloPage } from './routes/HelloPage'
import { LoginPage } from './routes/LoginPage'
import { RegisterPage } from './routes/RegisterPage'
import { ResetPasswordPage } from './routes/ResetPasswordPage'
import { CreateUserPage } from './routes/admin/CreateUserPage'
import { UserDetailPage } from './routes/admin/UserDetailPage'
import { UserListPage } from './routes/admin/UserListPage'

function Header(): ReactNode {
  const { user, logout } = useAuth()
  return (
    <header>
      <Link to="/" className="brand">
        Secured Login
      </Link>
      {user && (
        <nav>
          <span className="muted">
            {user.username} · {user.role}
          </span>
          <button type="button" onClick={() => void logout()}>
            Sign out
          </button>
        </nav>
      )}
    </header>
  )
}

/**
 * The route table.
 *
 * Nesting carries the policy: `RequireSession` wraps everything authenticated and enforces the forced
 * change before any child renders, and `RequireUserManager` nests inside it so an admin route can never
 * be reached by an account that still owes a password change.
 */
export function App(): ReactNode {
  return (
    <div className="shell">
      <Header />
      <main>
        <Routes>
          <Route element={<RequireAnonymous />}>
            <Route path="/login" element={<LoginPage />} />
            <Route path="/register" element={<RegisterPage />} />
            <Route path="/forgot-password" element={<ForgotPasswordPage />} />
            <Route path="/reset-password" element={<ResetPasswordPage />} />
          </Route>

          <Route element={<RequireSession />}>
            <Route path="/hello" element={<HelloPage />} />
            <Route path="/change-password" element={<ChangePasswordPage />} />
            <Route element={<RequireUserManager />}>
              <Route path="/admin/users" element={<UserListPage />} />
              <Route path="/admin/users/new" element={<CreateUserPage />} />
              <Route path="/admin/users/:userId" element={<UserDetailPage />} />
            </Route>
          </Route>

          <Route path="/" element={<Navigate to="/hello" replace />} />
          <Route path="*" element={<Navigate to="/hello" replace />} />
        </Routes>
      </main>
    </div>
  )
}
