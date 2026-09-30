import { useEffect } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import { primeCsrfCookie } from './api/client'
import RegisterPage from './pages/RegisterPage'
import LoginPage from './pages/LoginPage'
import HelloPage from './pages/HelloPage'
import ForgotPasswordPage from './pages/ForgotPasswordPage'
import ResetPasswordPage from './pages/ResetPasswordPage'
import AdminUsersPage from './pages/AdminUsersPage'

export default function App() {
  useEffect(() => {
    primeCsrfCookie()
  }, [])

  return (
    <Routes>
      <Route path="/register" element={<RegisterPage />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/hello" element={<HelloPage />} />
      <Route path="/forgot-password" element={<ForgotPasswordPage />} />
      <Route path="/reset-password" element={<ResetPasswordPage />} />
      <Route path="/admin/users" element={<AdminUsersPage />} />
      <Route path="*" element={<Navigate to="/login" replace />} />
    </Routes>
  )
}
