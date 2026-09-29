import { useEffect } from 'react'
import { Navigate, Route, Routes, useNavigate } from 'react-router'
import { onSessionEnded } from './api/client'
import { HelloPage } from './pages/HelloPage'
import { LoginPage } from './pages/LoginPage'
import { RegisterPage } from './pages/RegisterPage'

export default function App() {
  const navigate = useNavigate()

  // Global 401 handling: not logged in is a normal state, so go to login without showing an error.
  useEffect(() => onSessionEnded(() => navigate('/login', { replace: true })), [navigate])

  return (
    <main className="app">
      <Routes>
        <Route path="/" element={<HelloPage />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </main>
  )
}
