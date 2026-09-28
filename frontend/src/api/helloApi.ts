import { apiFetch } from './apiClient';

/**
 * Thin client around `GET /api/hello` (Story 5): a `text/plain` greeting when
 * authenticated, `401` otherwise. Failures (network or non-2xx) collapse to
 * `null` — same "caller doesn't need to know why" convention as `getMe()`.
 */
export async function getHello(): Promise<string | null> {
  let response: Response;
  try {
    response = await apiFetch('/api/hello');
  } catch {
    return null;
  }

  if (!response.ok) {
    return null;
  }

  return await response.text();
}
