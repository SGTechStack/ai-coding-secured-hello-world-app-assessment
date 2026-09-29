import { useState } from 'react'
import { Link, NavLink, Outlet, useNavigate } from 'react-router'
import { useAuth } from '../auth/useAuth'
import type { LoginRedirectState } from '../auth/guards'

export function Layout() {
  const { state, logout } = useAuth()
  const navigate = useNavigate()
  const [signingOut, setSigningOut] = useState(false)

  async function handleLogout() {
    setSigningOut(true)
    try {
      await logout()
    } catch {
      // The local session is cleared either way; the server session expires on its own.
    } finally {
      setSigningOut(false)
    }
    const redirect: LoginRedirectState = { notice: 'You have been signed out.' }
    navigate('/login', { replace: true, state: redirect })
  }

  return (
    <>
      <header className="site-header">
        <Link to="/" className="brand">
          Hello Auth
        </Link>
        <nav aria-label="Main">
          {state.status === 'authenticated' ? (
            <>
              <NavLink to="/" end>
                Home
              </NavLink>
              {state.user.role === 'ADMIN' && <NavLink to="/admin">Users</NavLink>}
              <span className="who">
                {state.user.username} <span className="badge">{state.user.role}</span>
              </span>
              <button type="button" className="link-button" onClick={handleLogout} disabled={signingOut}>
                Sign out
              </button>
            </>
          ) : (
            state.status === 'anonymous' && (
              <>
                <NavLink to="/login">Sign in</NavLink>
                <NavLink to="/register">Register</NavLink>
              </>
            )
          )}
        </nav>
      </header>
      <main>
        <Outlet />
      </main>
      <footer className="site-footer">
        <a href="https://tech.gov.sg/report_vulnerability" target="_blank" rel="noopener noreferrer">
          Report Vulnerability
        </a>
      </footer>
    </>
  )
}
