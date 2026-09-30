import type { ReactNode } from 'react';
import { createBrowserRouter, Navigate, RouterProvider } from 'react-router';
import { AuthProvider, useAuth } from './auth/AuthContext';
import { AdminUsersPage } from './pages/AdminUsersPage';
import { ForgotPasswordPage } from './pages/ForgotPasswordPage';
import { HomePage } from './pages/HomePage';
import { LoginPage } from './pages/LoginPage';
import { RegisterPage } from './pages/RegisterPage';
import { ResetPasswordPage } from './pages/ResetPasswordPage';

/** Hides pages that need a session; the API enforces access regardless. */
function RequireLogin({ children }: { children: ReactNode }) {
  const { me } = useAuth();
  return me ? children : <Navigate to="/login" replace />;
}

/** Hides admin pages from anyone `/me` doesn't report as ADMIN; the API enforces the role regardless. */
function RequireAdmin({ children }: { children: ReactNode }) {
  const { me } = useAuth();
  if (!me) return <Navigate to="/login" replace />;
  return me.role === 'ADMIN' ? children : <Navigate to="/" replace />;
}

const router = createBrowserRouter([
  { path: '/', element: <RequireLogin><HomePage /></RequireLogin> },
  { path: '/admin', element: <RequireAdmin><AdminUsersPage /></RequireAdmin> },
  { path: '/login', element: <LoginPage /> },
  { path: '/register', element: <RegisterPage /> },
  { path: '/forgot-password', element: <ForgotPasswordPage /> },
  { path: '/reset-password', element: <ResetPasswordPage /> },
  { path: '*', element: <Navigate to="/" replace /> },
]);

function Routes() {
  const { loading } = useAuth();
  return loading ? <p>Loading…</p> : <RouterProvider router={router} />;
}

export function App() {
  return (
    <AuthProvider>
      <Routes />
    </AuthProvider>
  );
}
