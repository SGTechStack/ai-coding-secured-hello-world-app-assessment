import { Navigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

export default function HomeRedirect() {
  const { status, user } = useAuth();

  if (status === 'loading') {
    return <p>Loading…</p>;
  }

  return <Navigate to={user ? '/hello' : '/login'} replace />;
}
