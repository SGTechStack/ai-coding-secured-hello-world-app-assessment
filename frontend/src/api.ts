// Thin API client. Auth is cookie-based (session), so all requests send credentials.
// CSRF token is read from the XSRF-TOKEN cookie set by the backend and echoed in a header.

export interface LoginResponse {
  username: string;
  role: string;
  mustChangePassword: boolean;
}

export interface AdminUser {
  id: string;
  username: string;
  email: string;
  role: "USER" | "ADMIN";
  enabled: boolean;
  createdAt: string;
}

function readCookie(name: string): string | null {
  const match = document.cookie.match(new RegExp("(^|;\\s*)" + name + "=([^;]*)"));
  return match ? decodeURIComponent(match[2]) : null;
}

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = {};
  if (body !== undefined) {
    headers["Content-Type"] = "application/json";
  }
  const stateChanging = method !== "GET" && method !== "HEAD";
  if (stateChanging) {
    const token = readCookie("XSRF-TOKEN");
    if (token) {
      headers["X-XSRF-TOKEN"] = token;
    }
  }

  const res = await fetch(path, {
    method,
    headers,
    credentials: "include",
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });

  const text = await res.text();
  const data = text ? JSON.parse(text) : null;
  if (!res.ok) {
    const message = (data && data.message) || "Request failed.";
    throw new Error(message);
  }
  return data as T;
}

// Ensure a CSRF cookie exists before submitting a state-changing request.
export async function primeCsrf(): Promise<void> {
  await fetch("/api/csrf", { credentials: "include" });
}

export const api = {
  primeCsrf,
  register: (username: string, email: string, password: string) =>
    request<{ message: string }>("POST", "/api/register", { username, email, password }),
  login: (username: string, password: string) =>
    request<LoginResponse>("POST", "/api/login", { username, password }),
  logout: () => request<{ message: string }>("POST", "/api/logout"),
  hello: () => request<{ message: string }>("GET", "/api/hello"),
  changePassword: (currentPassword: string, newPassword: string) =>
    request<{ message: string }>("POST", "/api/account/change-password", {
      currentPassword,
      newPassword,
    }),
  requestReset: (email: string) =>
    request<{ message: string }>("POST", "/api/password-reset/request", { email }),
  confirmReset: (token: string, password: string) =>
    request<{ message: string }>("POST", "/api/password-reset/confirm", { token, password }),
  adminListUsers: () => request<AdminUser[]>("GET", "/api/admin/users"),
  adminSetStatus: (id: string, enabled: boolean) =>
    request<AdminUser>("PATCH", `/api/admin/users/${id}/status`, { enabled }),
  adminChangeRole: (id: string, role: string) =>
    request<AdminUser>("PATCH", `/api/admin/users/${id}/role`, { role }),
  adminDelete: (id: string) =>
    request<{ message: string }>("DELETE", `/api/admin/users/${id}`),
};
