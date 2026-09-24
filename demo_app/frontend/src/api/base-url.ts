/**
 * Resolves the API base URL from `VITE_API_BASE_URL`. Shared by the client (which prefixes every
 * request with it) and `vite.config.ts` (which builds the CSP `connect-src` from it), so the two
 * can never disagree.
 */
export function apiBaseUrl(configured: string | undefined): string {
  return (configured || "http://localhost:8080").replace(/\/+$/, "");
}
