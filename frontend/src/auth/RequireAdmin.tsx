import type { ReactElement } from 'react';
import { Navigate } from 'react-router-dom';
import type { AuthenticatedUser } from '../api/authApi';

interface RequireAdminProps {
  /** The already-resolved session user, from `RequireAuth`'s render prop -- no second GET /me. */
  user: AuthenticatedUser;
  children: ReactElement;
}

/** Bounces a non-admin visitor to `/`. Nests inside `RequireAuth`, which has already handled anonymous visitors. */
export default function RequireAdmin({ user, children }: RequireAdminProps) {
  if (user.role !== 'ADMIN') {
    return <Navigate to="/" replace />;
  }

  return children;
}
