import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { api } from '../api/client'
import { useAuth } from '../context/AuthContext'

export default function HelloPage() {
  const navigate = useNavigate()
  const { role, clearSession } = useAuth()
  const [message, setMessage] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    api
      .get<{ message: string }>('/api/hello')
      .then((result) => setMessage(result.message))
      .catch(() => navigate('/login'))
      .finally(() => setLoading(false))
  }, [navigate])

  async function handleLogout() {
    await api.post('/api/auth/logout')
    clearSession()
    navigate('/login')
  }

  if (loading) {
    return <p>Loading…</p>
  }

  return (
    <main>
      <h1>{message}</h1>
      {role === 'ADMIN' && <Link to="/admin/users">Manage users</Link>}
      <p>
        <button onClick={handleLogout}>Log out</button>
      </p>
    </main>
  )
}
