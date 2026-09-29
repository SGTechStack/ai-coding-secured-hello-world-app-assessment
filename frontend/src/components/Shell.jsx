import { Link } from "react-router-dom";
import { useSession } from "../session";

export default function Shell({ children }) {
  const { user, logout } = useSession();

  return (
    <div className="frame">
      <header className="topbar">
        <Link className="wordmark" to="/">
          <span className="mark" aria-hidden="true" />
          Hello Desk
        </Link>
        <nav>
          {user ? (
            <>
              {user.role === "ADMIN" && <Link to="/admin">Directory</Link>}
              <button type="button" className="text-button" onClick={logout}>
                Log out
              </button>
            </>
          ) : (
            <>
              <Link to="/login">Log in</Link>
              <Link to="/register">Register</Link>
            </>
          )}
        </nav>
      </header>
      <main>{children}</main>
    </div>
  );
}
