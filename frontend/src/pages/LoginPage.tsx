import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { api, ApiError } from '../api/client'
import { useAuth } from '../context/AuthContext'

interface LoginResponse {
  username: string
  role: string
  forcePasswordChange: boolean
}

export default function LoginPage() {
  const navigate = useNavigate()
  const { setSession } = useAuth()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      const result = await api.post<LoginResponse>('/api/auth/login', { username, password })
      setSession(result)
      navigate('/hello')
    } catch (err) {
      // Deliberately generic — the backend never reveals whether the username exists.
      setError(err instanceof ApiError ? err.message : 'Login failed')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main>
      <h1>Log in</h1>
      <form onSubmit={handleSubmit}>
        <label>
          Username
          <input value={username} onChange={(e) => setUsername(e.target.value)} required />
        </label>
        <label>
          Password
          <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} required />
        </label>
        {error && (
          <p role="alert" style={{ color: 'crimson' }}>
            {error}
          </p>
        )}
        <button type="submit" disabled={submitting}>
          {submitting ? 'Logging in…' : 'Log in'}
        </button>
      </form>
      <p>
        <Link to="/forgot-password">Forgot password?</Link>
      </p>
      <p>
        No account? <Link to="/register">Register</Link>
      </p>
    </main>
  )
}
