import { useEffect, useState, type FormEvent, type ReactNode } from 'react'
import { Link, useNavigate } from 'react-router'
import { createUser, listRoles } from '../../api/endpoints'
import { ErrorBanner } from '../../components/Feedback'
import { describeError } from '../../components/errorText'
import { textField } from '../../components/formFields'

/**
 * Administrator-created accounts (story 1.15).
 *
 * The role list comes from `GET /roles` rather than being hard-coded, because the vocabulary is seeded
 * server-side and the authorization matrix names those strings directly — a hard-coded option that drifts
 * would produce a 400 the form could not explain.
 *
 * The created account is flagged for a forced password change, and the composition of the password the
 * administrator types is not constrained beyond the shared policy: the standard's one-of-each-class clause
 * was scoped to an admin-*generated* password, which this application does not do (ticket 11).
 */
export function CreateUserPage(): ReactNode {
  const navigate = useNavigate()
  const [roles, setRoles] = useState<string[]>([])
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    listRoles()
      .then(setRoles)
      .catch((failure: unknown) => setError(describeError(failure, 'The role list could not be loaded.')))
  }, [])

  async function onSubmit(event: FormEvent<HTMLFormElement>): Promise<void> {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    setError(null)
    setBusy(true)
    try {
      await createUser({
        username: textField(form, 'username'),
        email: textField(form, 'email'),
        password: textField(form, 'password'),
        role: textField(form, 'role'),
      })
      void navigate('/admin/users', { replace: true })
    } catch (failure) {
      // IDENTIFIER_UNAVAILABLE names which identifier collided, and that is deliberate: uniqueness is a
      // usability concern on a form, not an authentication outcome, so Std:247 does not scope to it.
      setError(describeError(failure, 'The account could not be created.'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="card">
      <h1>Create a user</h1>
      <ErrorBanner message={error} />
      <form onSubmit={onSubmit}>
        <label htmlFor="username">Username</label>
        <input id="username" name="username" required maxLength={100} />

        <label htmlFor="email">Email</label>
        <input id="email" name="email" type="email" required maxLength={255} />

        <label htmlFor="password">Initial password</label>
        <input
          id="password"
          name="password"
          type="password"
          autoComplete="new-password"
          required
          minLength={12}
          maxLength={72}
        />
        <p className="hint">
          12 to 72 characters. The user will be required to change it the first time they sign in.
        </p>

        <label htmlFor="role">Role</label>
        <select id="role" name="role" required defaultValue="USER">
          {roles.map((role) => (
            <option key={role} value={role}>
              {role}
            </option>
          ))}
        </select>

        <button type="submit" disabled={busy}>
          {busy ? 'Creating…' : 'Create user'}
        </button>
      </form>
      <p className="muted">
        <Link to="/admin/users">Back to users</Link>
      </p>
    </section>
  )
}
