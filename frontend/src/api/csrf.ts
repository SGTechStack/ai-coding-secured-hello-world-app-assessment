/**
 * Reads the `XSRF-TOKEN` cookie the backend sets on every response (Spring
 * Security's `CookieCsrfTokenRepository`). State-changing requests must echo
 * it back as the `X-XSRF-TOKEN` header, or the server rejects them with 403.
 */
export function readCsrfToken(): string | null {
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
  return match ? decodeURIComponent(match[1]) : null;
}
