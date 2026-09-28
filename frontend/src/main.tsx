import React from "react";
import ReactDOM from "react-dom/client";
import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import { AuthProvider, useAuth } from "./auth";
import { Login } from "./pages/Login";
import { Register } from "./pages/Register";
import { Greeting } from "./pages/Greeting";
import { RequestReset } from "./pages/RequestReset";
import { ConfirmReset } from "./pages/ConfirmReset";
import { ChangePassword } from "./pages/ChangePassword";
import { AdminUsers } from "./pages/AdminUsers";
import "./styles.css";

function RequireAuth({ children }: { children: React.ReactElement }) {
  const { user } = useAuth();
  if (!user) {
    return <Navigate to="/login" replace />;
  }
  if (user.mustChangePassword) {
    return <Navigate to="/change-password" replace />;
  }
  return children;
}

function RequireAdmin({ children }: { children: React.ReactElement }) {
  const { user } = useAuth();
  if (!user) {
    return <Navigate to="/login" replace />;
  }
  if (user.role !== "ADMIN") {
    return <Navigate to="/greeting" replace />;
  }
  return children;
}

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/" element={<Navigate to="/login" replace />} />
          <Route path="/login" element={<Login />} />
          <Route path="/register" element={<Register />} />
          <Route path="/forgot-password" element={<RequestReset />} />
          <Route path="/reset-password" element={<ConfirmReset />} />
          <Route path="/change-password" element={<ChangePassword />} />
          <Route path="/greeting" element={<RequireAuth><Greeting /></RequireAuth>} />
          <Route path="/admin" element={<RequireAdmin><AdminUsers /></RequireAdmin>} />
          <Route path="*" element={<Navigate to="/login" replace />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  </React.StrictMode>
);
