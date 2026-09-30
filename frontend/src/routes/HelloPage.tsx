import { useEffect, useState, type ReactNode } from 'react'
import { Link } from 'react-router'
import { greeting } from '../api/endpoints'
import { useAuth } from '../auth/useAuth'
import { ErrorBanner } from '../components/Feedback'
import { describeError } from '../components/errorText'

/** The protected greeting. A `USER_MANAGER` reaches it through the role hierarchy, with no explicit grant. */
export function HelloPage(): ReactNode {
  const { user } = useAuth()
  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    greeting()
      .then((value) => {
        if (!cancelled) {
          setMessage(value)
        }
      })
      .catch((failure: unknown) => {
        if (!cancelled) {
          setError(describeError(failure, 'The greeting could not be loaded.'))
        }
      })
    return () => {
      cancelled = true
    }
  }, [])

  return (
    <section className="card">
      <h1>{message ?? 'Loading…'}</h1>
      <ErrorBanner message={error} />
      <dl className="detail">
        <dt>Username</dt>
        <dd>{user?.username}</dd>
        <dt>Email</dt>
        <dd>{user?.email}</dd>
        <dt>Role</dt>
        <dd>{user?.role}</dd>
        <dt>Last sign-in</dt>
        <dd>{user?.lastLoginAt ?? 'this is your first'}</dd>
        <dt>Password last changed</dt>
        <dd>{user?.lastPasswordChangeAt ?? 'unknown'}</dd>
      </dl>
      <p className="muted">
        <Link to="/change-password">Change password</Link>
        {user?.role === 'USER_MANAGER' && (
          <>
            {' · '}
            <Link to="/admin/users">Manage users</Link>
          </>
        )}
      </p>
    </section>
  )
}
