import { useEffect, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { api } from "../api";
import { useSession } from "../session";

export default function HomePage() {
  const { user, logout } = useSession();
  const location = useLocation();
  const navigate = useNavigate();
  const [greeting, setGreeting] = useState("");
  const [error, setError] = useState("");
  const notice = location.state?.notice;

  useEffect(() => {
    let active = true;
    api("/api/hello")
      .then((body) => {
        if (active) setGreeting(body.message);
      })
      .catch(async (err) => {
        if (!active) return;
        if (err.status === 401) {
          await logout().catch(() => {});
          navigate("/login", { replace: true });
          return;
        }
        setError(err.message);
      });
    return () => {
      active = false;
    };
  }, [logout, navigate]);

  return (
    <section className="panel">
      <p className="kicker">{user?.role === "ADMIN" ? "Admin session" : "User session"}</p>
      <h1>{greeting || "Hello"}</h1>
      {notice && <p className="banner ok">{notice}</p>}
      {error && <p className="banner">{error}</p>}
      <p className="lede">
        Signed in as {user?.username}. This greeting comes from the protected API, so a cleared session cannot replay it.
      </p>
    </section>
  );
}
