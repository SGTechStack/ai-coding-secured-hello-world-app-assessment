import { NavLink, useNavigate } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';

export function Layout({ children }: { children: React.ReactNode }) {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = async () => {
    await logout();
    navigate('/login');
  };

  return (
    <div className="app-shell">
      <header className="app-header">
        <span className="app-header-brand">🔐 Hello World Auth</span>

        <nav className="app-nav" aria-label="Main navigation">
          {user && (
            <NavLink to="/" end className={({ isActive }) => `app-nav-link${isActive ? ' active' : ''}`}>
              Home
            </NavLink>
          )}
          {user?.role === 'ADMIN' && (
            <NavLink to="/admin" className={({ isActive }) => `app-nav-link${isActive ? ' active' : ''}`}>
              Admin
            </NavLink>
          )}
          {!user && (
            <>
              <NavLink to="/login" className={({ isActive }) => `app-nav-link${isActive ? ' active' : ''}`}>
                Login
              </NavLink>
              <NavLink to="/register" className={({ isActive }) => `app-nav-link${isActive ? ' active' : ''}`}>
                Register
              </NavLink>
            </>
          )}
        </nav>

        {user && (
          <div className="app-header-user">
            <span>
              {user.username}
              {' '}
              <span className={`badge ${user.role === 'ADMIN' ? 'badge-admin' : 'badge-user'}`}>
                {user.role}
              </span>
            </span>
            <button
              className="btn btn-secondary btn-sm"
              onClick={() => { void handleLogout(); }}
            >
              Sign out
            </button>
          </div>
        )}
      </header>

      <main className="app-main">
        {children}
      </main>
    </div>
  );
}
