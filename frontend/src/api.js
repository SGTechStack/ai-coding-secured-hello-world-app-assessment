const API_BASE = import.meta.env.VITE_API_BASE ?? "http://localhost:8080";

let csrfToken = null;

async function loadCsrfToken() {
  const response = await fetch(`${API_BASE}/api/auth/csrf`, {
    credentials: "include",
  });
  if (!response.ok) {
    throw new Error("Could not start a secure session.");
  }
  const body = await response.json();
  csrfToken = body.token;
  return csrfToken;
}

export async function api(path, { method = "GET", body, retry = true } = {}) {
  const headers = { Accept: "application/json" };
  if (body !== undefined) {
    headers["Content-Type"] = "application/json";
  }
  if (method !== "GET" && method !== "HEAD") {
    headers["X-XSRF-TOKEN"] = csrfToken ?? (await loadCsrfToken());
  }

  const response = await fetch(`${API_BASE}${path}`, {
    method,
    headers,
    credentials: "include",
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });

  if (response.status === 403 && retry && method !== "GET" && method !== "HEAD") {
    csrfToken = null;
    await loadCsrfToken();
    return api(path, { method, body, retry: false });
  }

  const text = await response.text();
  const data = text ? JSON.parse(text) : null;
  if (!response.ok) {
    const error = new Error(data?.message || "Request failed.");
    error.status = response.status;
    throw error;
  }
  return data;
}
