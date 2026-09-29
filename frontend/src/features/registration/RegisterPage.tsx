import { type FormEvent, useState } from 'react'
import { Link } from 'react-router'
import { useSubmitThenLogIn } from '../../shared/navigation/useSubmitThenLogIn.ts'
import { AuthCard } from '../../shared/ui/AuthCard.tsx'
import { register } from './api.ts'

export function RegisterPage() {
  const [username, setUsername] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const { error, submitting, submit: send } = useSubmitThenLogIn('Account created. Please log in.')

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    void send(() => register(username, email, password))
  }

  return (
    <AuthCard
      title="Create an account"
      description="It takes a few seconds. You can log in straight after."
      footer={
        <>
          <span className="muted">Already have an account?</span>
          <Link to="/login">Back to log in</Link>
        </>
      }
    >
      {error ? (
        <div role="alert" className="notice notice--error">
          {error}
        </div>
      ) : null}
      <form className="form-stack" noValidate onSubmit={submit}>
        <div className="field">
          <label htmlFor="register-username">Username</label>
          <input
            id="register-username"
            name="username"
            autoComplete="username"
            value={username}
            disabled={submitting}
            onChange={(event) => setUsername(event.currentTarget.value)}
          />
        </div>
        <div className="field">
          <label htmlFor="register-email">Email</label>
          <input
            id="register-email"
            name="email"
            type="email"
            autoComplete="email"
            value={email}
            disabled={submitting}
            onChange={(event) => setEmail(event.currentTarget.value)}
          />
        </div>
        <div className="field">
          <label htmlFor="register-password">Password</label>
          <input
            id="register-password"
            name="password"
            type="password"
            autoComplete="new-password"
            value={password}
            disabled={submitting}
            onChange={(event) => setPassword(event.currentTarget.value)}
          />
        </div>
        <button type="submit" className="btn btn--primary btn--block" disabled={submitting}>
          Create account
        </button>
      </form>
    </AuthCard>
  )
}
