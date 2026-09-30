export type CsrfResponse = { token: string; headerName: string; parameterName: string };

let csrf: CsrfResponse | undefined;
/** True once login succeeded: every token fetched from then on belongs to the authenticated session. */
let authenticated = false;
/**
 * True for a token fetched before login. False for none, or for the authenticated session's token, which is held for
 * the rest of the session and never re-fetched.
 */
let anonymous = false;
let pending: Promise<void> | undefined;

/** The header for the current in-memory token, as the server advertised it. */
export function csrfHeader(): { name: string; value: string } | undefined {
  return csrf ? { name: csrf.headerName, value: csrf.token } : undefined;
}

/**
 * After a successful login: the server replaced the session, so the anonymous token is dead. Drops it; the next
 * mutating request fetches the authenticated session's token from `GET /csrf`.
 */
export function startAuthenticatedCsrfSession(): void {
  resetCsrfToken();
  authenticated = true;
}

/** Drops all CSRF material when its session is retired (logout, confirmed 401). */
export function resetCsrfToken(): void {
  csrf = undefined;
  authenticated = false;
  anonymous = false;
  pending = undefined;
}

/**
 * A rejection says the authenticated session is gone: drops its CSRF material, like {@link resetCsrfToken}.
 * @returns true only for the first report per authenticated session; false before login or once already retired
 */
export function retireAuthenticatedCsrfSession(): boolean {
  if (!authenticated) return false;
  resetCsrfToken();
  return true;
}

/**
 * Ensures a token is held, fetching the anonymous session's token when none is. Concurrent callers share one fetch.
 * A fetch that completes after the token was adopted or reset is discarded, so it can never replace a newer token.
 */
export function ensureCsrfToken(fetchToken: () => Promise<CsrfResponse>): Promise<void> {
  if (csrf) return Promise.resolve();
  if (pending) return pending;
  const request: Promise<void> = fetchToken()
    .then((token) => {
      if (pending !== request) return;
      csrf = token;
      anonymous = !authenticated;
    })
    .finally(() => {
      if (pending === request) pending = undefined;
    });
  pending = request;
  return request;
}

/**
 * After a 403 on a request sent with `sentToken`, decides whether a retry can help. An authenticated token is never
 * re-fetched: after login a 403 means access was denied. An anonymous token is replaced once, however many refused
 * requests report it: whoever arrives after the replacement sees a newer token and just retries.
 */
export async function refreshAnonymousCsrfToken(
  sentToken: string | undefined,
  fetchToken: () => Promise<CsrfResponse>,
): Promise<boolean> {
  if (csrf && csrf.token !== sentToken) return true;
  if (!anonymous) return false;
  csrf = undefined;
  await ensureCsrfToken(fetchToken);
  return true;
}
