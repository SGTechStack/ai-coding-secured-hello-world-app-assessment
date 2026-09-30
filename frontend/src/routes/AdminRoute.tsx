import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

export default function AdminRoute() {
  const { status, user } = useAuth();
  const location = useLocation();

  if (status === 'loading') {
    return <p>Loading…</p>;
  }

  if (status === 'anonymous') {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  if (user?.role !== 'ADMIN') {
    return <Navigate to="/hello" replace />;
  }

  return <Outlet />;
}
