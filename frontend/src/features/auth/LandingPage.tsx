import { useState } from 'react'
import { Link, useLoaderData, useNavigate, useRouteLoaderData } from 'react-router'
import { SERVER_UNAVAILABLE } from '../../shared/api/http.ts'
import { TopBar } from '../../shared/ui/TopBar.tsx'
import { type CurrentUser, logout } from './api.ts'
import type { landingLoader } from './loaders.ts'
import './LandingPage.css'

export function LandingPage() {
  const { greeting } = useLoaderData<typeof landingLoader>()
  const currentUser = useRouteLoaderData('protected') as CurrentUser
  const navigate = useNavigate()
  const [error, setError] = useState<string>()
  const isAdmin = currentUser.role === 'ADMIN'

  async function handleLogout() {
    setError(undefined)
    const result = await logout()
    if (result.status === 'success' || result.status === 'forbidden') {
      void navigate('/login', { replace: true })
    } else if (result.status === 'unavailable') {
      setError(SERVER_UNAVAILABLE)
    }
  }

  return (
    <div className="app-page">
      <TopBar>
        <span className="user-chip">
          <span className="avatar" aria-hidden="true">
            {currentUser.username.slice(0, 1)}
          </span>
          <span className="user-chip__name">{currentUser.username}</span>
          <span className="role-pill">{currentUser.role}</span>
        </span>
        <button
          type="button"
          className="btn btn--ghost btn--small"
          onClick={() => void handleLogout()}
        >
          Log out
        </button>
      </TopBar>
      <main className="app-main">
        <section className="landing-hero rise-in">
          <p className="eyebrow">Dashboard</p>
          <h1 className="landing-hero__title">{greeting}</h1>
          <p className="lede">You're signed in. Your session stays active until you log out.</p>
          {error && (
            <p role="alert" className="notice notice--error">
              {error}
            </p>
          )}
        </section>
        {isAdmin ? (
          <section className="tile tile--accent panel rise-in" aria-labelledby="admin-tile">
            <h2 id="admin-tile">User management</h2>
            <p className="muted">Enable, disable, promote or remove accounts.</p>
            <Link className="btn btn--primary" to="/admin/users">
              Manage users
            </Link>
          </section>
        ) : null}
      </main>
    </div>
  )
}
