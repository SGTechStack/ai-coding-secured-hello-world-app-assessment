// API client for the Spring Boot backend. The backend lives on its own origin
// (default http://localhost:8080), so every call is cross-origin. `credentials:
// 'include'` sends/receives the session cookie added in a later slice; the
// backend CORS allow-list must permit this origin with credentials.
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080';

export interface HealthResponse {
  status: string;
  time: string;
}

export async function fetchHealth(): Promise<HealthResponse> {
  const response = await fetch(`${API_BASE_URL}/api/health`, {
    method: 'GET',
    credentials: 'include',
  });

  if (!response.ok) {
    throw new Error(`Health check failed: HTTP ${response.status}`);
  }

  return (await response.json()) as HealthResponse;
}
