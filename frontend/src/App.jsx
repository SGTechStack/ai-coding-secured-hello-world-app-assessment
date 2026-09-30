import { useEffect, useState } from 'react';
import { Routes, Route, Link, useNavigate, Navigate } from 'react-router-dom';
import { api, primeCsrf } from './api.js';
import Login from './pages/Login.jsx';
import Register from './pages/Register.jsx';
import Hello from './pages/Hello.jsx';
import ForgotPassword from './pages/ForgotPassword.jsx';
import ResetPassword from './pages/ResetPassword.jsx';
import Admin from './pages/Admin.jsx';

export default function App() {
  const [user, setUser] = useState(null);
  const [ready, setReady] = useState(false);
  const navigate = useNavigate();

  // On load, prime the CSRF cookie and probe whether a session already exists.
  useEffect(() => {
    (async () => {
      await primeCsrf();
      try {
        const res = await api.get('/api/hello');
        // "Hello, <username>"
        const name = res.message.replace(/^Hello,\s*/, '');
        setUser({ username: name });
      } catch {
        setUser(null);
      } finally {
        setReady(true);
      }
    })();
  }, []);

  async function handleLogout() {
    try {
      await api.post('/api/auth/logout');
    } catch {
      /* ignore */
    }
    setUser(null);
    navigate('/login');
  }

  if (!ready) {
    return (
      <div className="app-shell">
        <div className="big-top"><h1>🎪 Loading the Big Top… 🎪</h1></div>
      </div>
    );
  }

  return (
    <div className="app-shell">
      <header className="big-top">
        <h1>🎪 The Greatest Login on Earth 🎪</h1>
        <p>Step right up — secure seats only!</p>
      </header>

      <nav className="ringmaster">
        <Link to="/">🎟️ Hello</Link>
        {!user && <Link to="/login">🤹 Login</Link>}
        {!user && <Link to="/register">🎨 Register</Link>}
        {!user && <Link to="/forgot-password">🎭 Forgot?</Link>}
        {user && <Link to="/admin">🦁 Admin</Link>}
        {user && (
          <button className="linkish" onClick={handleLogout}>
            🚪 Logout ({user.username})
          </button>
        )}
      </nav>

      <Routes>
        <Route path="/" element={<Hello user={user} />} />
        <Route path="/login" element={<Login onLoggedIn={setUser} />} />
        <Route path="/register" element={<Register />} />
        <Route path="/forgot-password" element={<ForgotPassword />} />
        <Route path="/reset-password" element={<ResetPassword />} />
        <Route
          path="/admin"
          element={user ? <Admin currentUser={user} /> : <Navigate to="/login" replace />}
        />
      </Routes>
    </div>
  );
}
