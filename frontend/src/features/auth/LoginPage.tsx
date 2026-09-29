import { type FormEvent, useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router'
import { SERVER_UNAVAILABLE } from '../../shared/api/http.ts'
import { login } from './api.ts'
import { readLoginStatus } from '../../shared/navigation/loginStatus.ts'
import { AuthCard } from '../../shared/ui/AuthCard.tsx'
import './LoginPage.css'

const INVALID_CREDENTIALS = 'Invalid username or password'
const MINIMUM_SUBMISSION_MS = 400

type FieldErrors = {
  username?: string
  password?: string
}

function wait(milliseconds: number): Promise<void> {
  return new Promise((resolve) => window.setTimeout(resolve, milliseconds))
}

export function LoginPage() {
  const navigate = useNavigate()
  const status = readLoginStatus(useLocation().state)
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({})
  const [banner, setBanner] = useState<string>()
  const [submitting, setSubmitting] = useState(false)

  function changeUsername(value: string) {
    setUsername(value)
    setFieldErrors((current) => ({ ...current, username: undefined }))
    setBanner(undefined)
  }

  function changePassword(value: string) {
    setPassword(value)
    setFieldErrors((current) => ({ ...current, password: undefined }))
    setBanner(undefined)
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const nextErrors: FieldErrors = {}
    if (username.trim() === '') nextErrors.username = 'Username is required'
    if (password.trim() === '') nextErrors.password = 'Password is required'
    setFieldErrors(nextErrors)
    if (nextErrors.username || nextErrors.password) return

    setSubmitting(true)
    setBanner(undefined)
    const resultPromise = login(username, password)
    const [result] = await Promise.all([resultPromise, wait(MINIMUM_SUBMISSION_MS)])

    if (result.status === 'success') {
      await navigate('/', { replace: true })
      return
    }

    setBanner(result.status === 'invalidCredentials' ? INVALID_CREDENTIALS : SERVER_UNAVAILABLE)
    setSubmitting(false)
  }

  return (
    <AuthCard
      title="Log in"
      description="Welcome back. Enter your details to continue."
      footer={
        <>
          <Link to="/forgot-password">Forgot password?</Link>
          <Link to="/register">Create an account</Link>
        </>
      }
    >
      {status && !banner ? (
        <p role="status" className="notice notice--success">
          {status}
        </p>
      ) : null}
      {banner ? (
        <div role="alert" className="notice notice--error">
          {banner}
        </div>
      ) : null}
      <form className="form-stack" onSubmit={submit}>
        <div className="field">
          <label htmlFor="username">Username</label>
          <input
            id="username"
            name="username"
            autoComplete="username"
            value={username}
            disabled={submitting}
            aria-describedby={fieldErrors.username ? 'username-error' : undefined}
            aria-invalid={Boolean(fieldErrors.username)}
            onChange={(event) => changeUsername(event.currentTarget.value)}
          />
          {fieldErrors.username ? (
            <div id="username-error" className="field-error">
              {fieldErrors.username}
            </div>
          ) : null}
        </div>
        <div className="field">
          <label htmlFor="password">Password</label>
          <input
            id="password"
            name="password"
            type="password"
            autoComplete="current-password"
            value={password}
            disabled={submitting}
            aria-describedby={fieldErrors.password ? 'password-error' : undefined}
            aria-invalid={Boolean(fieldErrors.password)}
            onChange={(event) => changePassword(event.currentTarget.value)}
          />
          {fieldErrors.password ? (
            <div id="password-error" className="field-error">
              {fieldErrors.password}
            </div>
          ) : null}
        </div>
        <button type="submit" className="btn btn--primary btn--block" disabled={submitting}>
          {submitting ? (
            <>
              <span className="login-spinner" aria-hidden="true" />
              <span>
                Logging in<span className="login-ellipsis">...</span>
              </span>
            </>
          ) : (
            'Log in'
          )}
        </button>
      </form>
    </AuthCard>
  )
}
