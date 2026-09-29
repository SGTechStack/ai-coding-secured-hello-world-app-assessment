import { Link, useLocation } from 'react-router'

/** Placeholder until the login ticket adds the form. */
export function LoginPage() {
  const registered = (useLocation().state as { registered?: boolean } | null)?.registered === true
  return (
    <section>
      <h1>Log in</h1>
      {registered && <p role="status">Your Account has been created. Please log in.</p>}
      <p>You are not logged in. The login form is coming soon.</p>
      <p>
        No Account yet? <Link to="/register">Register</Link>
      </p>
    </section>
  )
}
