import axios, { type AxiosRequestConfig, type InternalAxiosRequestConfig } from 'axios';
import {
  csrfHeader,
  ensureCsrfToken,
  refreshAnonymousCsrfToken,
  retireAuthenticatedCsrfSession,
  type CsrfResponse,
} from './csrf';
import { problemDetailSchema, type ProblemDetailJson } from './problem-detail';

const SAFE_METHODS = ['GET', 'HEAD', 'OPTIONS'];
type CsrfAwareConfig = InternalAxiosRequestConfig & {
  csrfToken?: string;
  csrfRetried?: boolean;
  ownsRejections?: boolean;
};

/**
 * The one HTTP transport. It owns the CSRF token for every mutating request, so no caller handles CSRF: the anonymous
 * token is fetched on the first mutating request (never by rendering a page), attached as the server-advertised
 * header, and replaced once when an anonymous session expired. After login the anonymous token is dropped and the
 * authenticated session's token is fetched from `/csrf` the same way.
 */
export const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL as string | undefined,
  withCredentials: true,
});

/**
 * Request option for a caller that handles its own CSRF 403s and 401s: the transport never retries that request and
 * never reports its rejection as Session expired.
 */
export const WITHOUT_CSRF_RETRY = { csrfRetried: true, ownsRejections: true } as AxiosRequestConfig;

/** Problem Detail codes (ADR 0001) that, after login, mean the Session is gone from the client's point of view. */
const SESSION_REJECTIONS = new Set<ProblemDetailJson['code']>(['AUTHENTICATION_REQUIRED', 'CSRF_TOKEN_REJECTED']);
let sessionExpired: (() => void) | undefined;

/**
 * Registers the one reaction to Session expired (or clears it with `undefined`). It is called at most once per
 * authenticated session: the transport drops the session's CSRF material first, so later rejections are not reported.
 */
export function onSessionExpired(listener: (() => void) | undefined): void {
  sessionExpired = listener;
}

/** Fetches the current session's CSRF token; the transport and Session restore hold it via `ensureCsrfToken`. */
export const fetchCsrfToken = async () => (await apiClient.get<CsrfResponse>('/csrf')).data;
// Never throws: a malformed or non-JSON body yields undefined.
const problemCode = (data: ProblemDetailJson | undefined) => problemDetailSchema.safeParse(data).data?.code;

/**
 * True for an Authentication-required rejection, told apart by its Problem Detail code (ADR 0001), not its status: a
 * 401 without that code (e.g. from a proxy) is an ordinary failure. After login the Session-expired reaction owns it,
 * so a page never shows it.
 */
export const isAuthenticationRequired = (error: unknown) =>
  axios.isAxiosError<ProblemDetailJson>(error) && problemCode(error.response?.data) === 'AUTHENTICATION_REQUIRED';

// `||`, not `??`: an empty method string is treated as GET (safe), exactly like a missing one.
const isUnsafe = (config: InternalAxiosRequestConfig) => !SAFE_METHODS.includes((config.method || 'GET').toUpperCase());

apiClient.interceptors.request.use(async (config: CsrfAwareConfig) => {
  if (!isUnsafe(config)) return config;
  await ensureCsrfToken(fetchCsrfToken);
  const header = csrfHeader();
  if (header) {
    config.headers.set(header.name, header.value);
    config.csrfToken = header.value;
  }
  return config;
});

// A 403 on an anonymous mutating request usually means the anonymous session (and its token) expired: retry exactly
// once with a fresh token. Authenticated tokens are never re-fetched (see refreshAnonymousCsrfToken): after login an
// AUTHENTICATION_REQUIRED 401 or CSRF_TOKEN_REJECTED 403 is reported as Session expired instead.
apiClient.interceptors.response.use(undefined, async (error: unknown) => {
  if (!axios.isAxiosError<ProblemDetailJson>(error) || !error.config) throw error;
  const config = error.config as CsrfAwareConfig;
  if (
    error.response?.status === 403 &&
    !config.csrfRetried &&
    isUnsafe(config) &&
    (await refreshAnonymousCsrfToken(config.csrfToken, fetchCsrfToken))
  ) {
    config.csrfRetried = true;
    return apiClient.request(config);
  }
  if (
    !config.ownsRejections &&
    SESSION_REJECTIONS.has(problemCode(error.response?.data)) &&
    retireAuthenticatedCsrfSession()
  )
    sessionExpired?.();
  throw error;
});
