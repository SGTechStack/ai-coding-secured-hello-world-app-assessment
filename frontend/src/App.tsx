import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import RedirectIfAuthenticated from './auth/RedirectIfAuthenticated';
import RequireAdmin from './auth/RequireAdmin';
import RequireAuth from './auth/RequireAuth';
import AdminPage from './pages/AdminPage';
import LandingPage from './pages/LandingPage';
import LoginPage from './pages/LoginPage';
import PasswordResetConfirmPage from './pages/PasswordResetConfirmPage';
import PasswordResetRequestPage from './pages/PasswordResetRequestPage';
import RegisterPage from './pages/RegisterPage';

/**
 * Top-level route wiring, with both auth-state route guards from issue 04:
 * an anonymous visitor is bounced from `/` to `/login`, and an
 * already-authenticated visitor is bounced from `/login` to `/`. Both
 * guards resolve session state via `GET /api/auth/me` (see
 * `auth/useSessionStatus.ts`), which is also what makes a browser
 * refresh restore auth state instead of losing it.
 */
export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route
          path="/login"
          element={
            <RedirectIfAuthenticated>
              <LoginPage />
            </RedirectIfAuthenticated>
          }
        />
        <Route
          path="/register"
          element={
            <RedirectIfAuthenticated>
              <RegisterPage />
            </RedirectIfAuthenticated>
          }
        />
        <Route path="/reset-password" element={<PasswordResetRequestPage />} />
        <Route path="/reset-password/confirm" element={<PasswordResetConfirmPage />} />
        <Route
          path="/"
          element={<RequireAuth>{(user) => <LandingPage user={user} />}</RequireAuth>}
        />
        <Route
          path="/admin"
          element={
            <RequireAuth>
              {(user) => (
                <RequireAdmin user={user}>
                  <AdminPage currentUsername={user.username} />
                </RequireAdmin>
              )}
            </RequireAuth>
          }
        />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}
