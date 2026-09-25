/**
 * Reads the password reset token out of the address bar.
 *
 * The fragment, not the query string: the browser never sends it to a
 * server, so the token cannot reach this host's access logs the way a query
 * parameter did before it could be stripped.
 */
export function readResetTokenFromUrl(): string | null {
  return new URLSearchParams(window.location.hash.replace(/^#/, "")).get("token");
}

/** True while a reset token is still sitting in the address bar's fragment. */
export function hasResetTokenInUrl(): boolean {
  return new URLSearchParams(window.location.hash.replace(/^#/, "")).has("token");
}

/**
 * Removes the reset token from the address bar without navigating.
 *
 * Keeps it out of browser history entries, and stops a page refresh from
 * dropping the user back into the reset flow holding a token that has
 * already been consumed.
 */
export function clearResetTokenFromUrl(): void {
  window.history.replaceState({}, "", window.location.pathname);
}
