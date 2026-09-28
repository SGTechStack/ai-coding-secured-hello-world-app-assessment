import { Navigate, Route, Routes } from "react-router-dom";
import { RequireAdmin, RequireAuth, RequireVisitor } from "./auth/guards";
import { AppLayout } from "./components/AppLayout";
import { AdminPage } from "./pages/AdminPage";
import { ForgotPasswordPage } from "./pages/ForgotPasswordPage";
import { HomePage } from "./pages/HomePage";
import { LoginPage } from "./pages/LoginPage";
import { NotFoundPage } from "./pages/NotFoundPage";
import { RegisterPage } from "./pages/RegisterPage";
import { ResetPasswordPage } from "./pages/ResetPasswordPage";

/**
 * The routes, one per story.
 *
 * The guards here decide what is rendered, not what is permitted — the API re-checks every rule on
 * every request. Anyone can edit their way past a client-side route; nobody can edit their way past
 * the filter chain.
 */
export function App() {
  return (
    <Routes>
      <Route
        path="/login"
        element={
          <RequireVisitor>
            <LoginPage />
          </RequireVisitor>
        }
      />
      <Route
        path="/register"
        element={
          <RequireVisitor>
            <RegisterPage />
          </RequireVisitor>
        }
      />
      {/* Both reset pages stay open to anyone: someone who has forgotten their password is, by
          definition, not logged in, and the token in the link is the only credential required. */}
      <Route path="/forgot-password" element={<ForgotPasswordPage />} />
      <Route path="/reset-password" element={<ResetPasswordPage />} />

      <Route
        element={
          <RequireAuth>
            <AppLayout />
          </RequireAuth>
        }
      >
        <Route path="/" element={<HomePage />} />
        <Route
          path="/admin"
          element={
            <RequireAdmin>
              <AdminPage />
            </RequireAdmin>
          }
        />
      </Route>

      <Route path="/index.html" element={<Navigate to="/" replace />} />
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  );
}
