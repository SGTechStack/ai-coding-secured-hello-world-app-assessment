import { NavLink } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

export default function NavBar() {
  const { status, user } = useAuth();

  return (
    <nav className="nav-bar">
      {status === 'authenticated' && user ? (
        <>
          <NavLink to="/hello">Hello</NavLink>
          {user.role === 'ADMIN' && <NavLink to="/admin">Admin</NavLink>}
          <LogoutLink />
        </>
      ) : (
        <>
          <NavLink to="/login">Login</NavLink>
          <NavLink to="/register">Register</NavLink>
        </>
      )}
    </nav>
  );
}

function LogoutLink() {
  const { logout } = useAuth();

  return (
    <button
      type="button"
      className="nav-link-button"
      onClick={() => {
        void logout();
      }}
    >
      Logout
    </button>
  );
}
