import type { ReactNode } from 'react';
import { Navigate } from 'react-router';
import { RequireAuth } from './RequireAuth';
import { useAuth } from './useAuth';

/**
 * Route guard for the admin module. This is UX only: the server enforces ROLE_ADMIN on every
 * /api/admin request regardless of what the client renders.
 */
export function RequireAdmin({ children }: { children: ReactNode }) {
  return (
    <RequireAuth>
      <AdminOnly>{children}</AdminOnly>
    </RequireAuth>
  );
}

function AdminOnly({ children }: { children: ReactNode }) {
  const { isAdmin } = useAuth();
  if (!isAdmin) {
    return <Navigate to="/" replace />;
  }
  return <>{children}</>;
}
