const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";

export interface HealthResponse {
  status: string;
  timestamp: string;
}

/**
 * Calls the backend's unauthenticated health endpoint across origins.
 * `credentials: "include"` is set now so the pattern is already in place
 * once session cookies exist for authenticated calls.
 */
export async function fetchHealth(): Promise<HealthResponse> {
  const response = await fetch(`${API_BASE_URL}/api/health`, {
    method: "GET",
    credentials: "include",
  });

  if (!response.ok) {
    throw new Error(`Health check failed with status ${response.status}`);
  }

  return response.json() as Promise<HealthResponse>;
}
