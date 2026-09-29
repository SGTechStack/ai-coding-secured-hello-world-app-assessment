import { Navigate, Route, Routes } from "react-router-dom";
import { useSession } from "./session";
import Shell from "./components/Shell";
import LoginPage from "./pages/LoginPage";
import RegisterPage from "./pages/RegisterPage";
import ForgotPasswordPage from "./pages/ForgotPasswordPage";
import ResetPasswordPage from "./pages/ResetPasswordPage";
import HomePage from "./pages/HomePage";
import AdminPage from "./pages/AdminPage";

function RequireAuth({ admin = false, children }) {
  const { user, loading } = useSession();
  if (loading) {
    return <p className="status">Checking your session…</p>;
  }
  if (!user) {
    return <Navigate to="/login" replace />;
  }
  if (admin && user.role !== "ADMIN") {
    return <Navigate to="/" replace />;
  }
  return children;
}

export default function App() {
  return (
    <Shell>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/forgot-password" element={<ForgotPasswordPage />} />
        <Route path="/reset-password" element={<ResetPasswordPage />} />
        <Route
          path="/"
          element={
            <RequireAuth>
              <HomePage />
            </RequireAuth>
          }
        />
        <Route
          path="/admin"
          element={
            <RequireAuth admin>
              <AdminPage />
            </RequireAuth>
          }
        />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </Shell>
  );
}
