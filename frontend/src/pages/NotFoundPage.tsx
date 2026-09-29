import { Link } from 'react-router'

export function NotFoundPage() {
  return (
    <section className="card">
      <h1>Page not found</h1>
      <p className="links">
        <Link to="/">Go home</Link>
      </p>
    </section>
  )
}
