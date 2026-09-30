import type { ReactElement } from 'react';
import { Navigate } from 'react-router-dom';
import { useSessionStatus } from './useSessionStatus';

interface RedirectIfAuthenticatedProps {
  children: ReactElement;
}

/** Bounces an already-authenticated visitor away from `/login`; renders nothing mid-check. */
export default function RedirectIfAuthenticated({ children }: RedirectIfAuthenticatedProps) {
  const status = useSessionStatus();

  if (status.state === 'checking') {
    return null;
  }

  if (status.state === 'authenticated') {
    return <Navigate to="/" replace />;
  }

  return children;
}
