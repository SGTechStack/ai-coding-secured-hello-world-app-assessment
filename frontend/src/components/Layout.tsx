import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';

export function Layout({ children }: { children: React.ReactNode }) {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = async () => {
    await logout();
    navigate('/login');
  };

  return (
    <div style={{ fontFamily: 'system-ui, sans-serif', maxWidth: 800, margin: '0 auto', padding: '1rem' }}>
      <header style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', borderBottom: '1px solid #ccc', paddingBottom: '0.5rem', marginBottom: '1.5rem' }}>
        <nav style={{ display: 'flex', gap: '1rem' }}>
          {user && <Link to="/">Hello</Link>}
          {user?.role === 'ADMIN' && <Link to="/admin">Admin</Link>}
          {!user && <Link to="/login">Login</Link>}
          {!user && <Link to="/register">Register</Link>}
        </nav>
        {user && (
          <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
            <span style={{ fontSize: '0.9rem', color: '#555' }}>{user.username} ({user.role})</span>
            <button onClick={() => { void handleLogout(); }} style={{ cursor: 'pointer' }}>Logout</button>
          </div>
        )}
      </header>
      <main>{children}</main>
    </div>
  );
}
