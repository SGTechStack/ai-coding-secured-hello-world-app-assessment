import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { type AuthenticatedUser, logout } from '../api/authApi';
import { messageFor } from '../api/errors';
import { getHello } from '../api/helloApi';
import './LandingPage.css';

interface LandingPageProps {
  /** Supplied by the `RequireAuth` route guard, which has already resolved the session. */
  user: AuthenticatedUser;
}

/**
 * Minimal authenticated landing page: greets the user and offers a logout
 * control. No dashboard content is in scope (spec: Further Notes).
 *
 * <p>The greeting is the literal text of `GET /api/hello` (Story 5), not a
 * client-rendered string -- this is what actually proves the session
 * survived the round trip, rather than just echoing back the already-known
 * `user.username` from route-guard state. Falls back to a client-built
 * greeting only if that call itself fails despite an authenticated session
 * (e.g. a dropped request).
 */
export default function LandingPage({ user }: LandingPageProps) {
  const [greeting, setGreeting] = useState<string | null>(null);
  const [isLoggingOut, setIsLoggingOut] = useState(false);
  const [logoutError, setLogoutError] = useState<string | null>(null);
  const navigate = useNavigate();

  useEffect(() => {
    let cancelled = false;

    getHello().then((text) => {
      if (!cancelled) {
        setGreeting(text);
      }
    });

    return () => {
      cancelled = true;
    };
  }, []);

  async function handleLogout() {
    setIsLoggingOut(true);
    setLogoutError(null);
    try {
      await logout();
    } catch (error) {
      // The server may still hold a live session: say so rather than
      // pretending the user is logged out.
      setLogoutError(messageFor(error));
      setIsLoggingOut(false);
      return;
    }
    navigate('/login', { replace: true });
  }

  return (
    <div className="landing-page">
      <aside className="landing-sidebar">
        <p className="landing-sidebar-brand">Auth App</p>
        <nav className="landing-sidebar-nav">
          {user.role === 'ADMIN' && (
            <Link to="/admin" className="landing-sidebar-link">
              Admin panel
            </Link>
          )}
        </nav>
        <button
          type="button"
          className="landing-sidebar-logout"
          onClick={handleLogout}
          disabled={isLoggingOut}
        >
          Log out
        </button>
      </aside>
      <main className="landing-content">
        {logoutError && (
          <p role="alert" className="landing-banner">
            {logoutError}
          </p>
        )}
        <h1>{greeting ?? `Hello, ${user.username}`}</h1>
      </main>
    </div>
  );
}
