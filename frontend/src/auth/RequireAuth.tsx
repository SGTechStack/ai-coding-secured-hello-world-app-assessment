import type { ReactElement } from 'react';
import { Navigate } from 'react-router-dom';
import type { AuthenticatedUser } from '../api/authApi';
import { useSessionStatus } from './useSessionStatus';

interface RequireAuthProps {
  /** Render prop so the guarded page gets the already-fetched session user, not a second GET /me. */
  children: (user: AuthenticatedUser) => ReactElement;
}

/** Bounces an anonymous visitor to `/login`; renders nothing while the session check is in flight. */
export default function RequireAuth({ children }: RequireAuthProps) {
  const status = useSessionStatus();

  if (status.state === 'checking') {
    return null;
  }

  if (status.state === 'unauthenticated') {
    return <Navigate to="/login" replace />;
  }

  return children(status.user);
}
