import { Link } from 'react-router'

export function AccessDeniedPage() {
  return (
    <section className="card">
      <h1>Access denied</h1>
      <p className="muted">You do not have permission to view this page.</p>
      <p className="links">
        <Link to="/">Go home</Link>
      </p>
    </section>
  )
}
