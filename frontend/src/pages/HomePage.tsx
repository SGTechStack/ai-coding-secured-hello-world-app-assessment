import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { api, ApiError } from '../api/client';
import { useAuth } from '../auth/AuthContext';

export function HomePage() {
  const { me, logout } = useAuth();
  const navigate = useNavigate();
  const [greeting, setGreeting] = useState<string | null>(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    api.hello().then(setGreeting, (error: unknown) => {
      // A lost session isn't an error to show: the 401 handler is already sending the user to login.
      if (!(error instanceof ApiError && error.status === 401)) setFailed(true);
    });
  }, []);

  async function onLogout() {
    await logout();
    navigate('/login', { replace: true });
  }

  return (
    <main>
      <h1>{greeting ?? (failed ? 'Could not load your greeting.' : 'Loading…')}</h1>
      {me?.role === 'ADMIN' && (
        <nav>
          <Link to="/admin">Manage accounts</Link>
        </nav>
      )}
      <button type="button" onClick={onLogout}>
        Log out
      </button>
    </main>
  );
}
