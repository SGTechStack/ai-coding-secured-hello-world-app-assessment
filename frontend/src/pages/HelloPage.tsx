import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

export default function HelloPage() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  async function handleLogout() {
    await logout();
    navigate('/login', { replace: true });
  }

  return (
    <section>
      <h2>Hello, {user?.username}</h2>
      <p>You are logged in as <strong>{user?.role}</strong>.</p>
      <button type="button" onClick={() => void handleLogout()}>
        Log out
      </button>
    </section>
  );
}
