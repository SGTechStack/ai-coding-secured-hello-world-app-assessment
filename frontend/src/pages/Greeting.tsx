import { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { api } from "../api";
import { useAuth } from "../auth";

export function Greeting() {
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const { user, setUser } = useAuth();
  const navigate = useNavigate();

  useEffect(() => {
    api.hello()
      .then((res) => setMessage(res.message))
      .catch((err) => setError((err as Error).message));
  }, []);

  async function onLogout() {
    try {
      await api.logout();
    } finally {
      setUser(null);
      navigate("/login");
    }
  }

  return (
    <div className="container">
      <div className="topbar">
        <h1>Greeting</h1>
        <button className="secondary" onClick={onLogout}>Log out</button>
      </div>
      {message && <p className="success">{message}</p>}
      {error && <p className="error">{error}</p>}
      {user?.role === "ADMIN" && (
        <div className="links">
          <Link to="/admin">Admin: manage users</Link>
        </div>
      )}
    </div>
  );
}
