const WEB_BASE_URL = process.env.WEB_BASE_URL ?? "http://localhost:3000";
const API_BASE_URL = process.env.API_BASE_URL ?? "http://localhost:8080";

/**
 * Fails fast, and for the right reason.
 *
 * Without this, a stopped backend shows up as a browser test timing out on a form submission — which
 * sends whoever is reading the failure to look at the form.
 */
async function requireReachable(label: string, url: string) {
  try {
    const response = await fetch(url);
    if (!response.ok) {
      throw new Error(`responded ${response.status}`);
    }
  } catch (cause) {
    throw new Error(
      `${label} is not reachable at ${url}. Start it before running these tests:\n` +
        `  cd api && APP_THROTTLE_MAX_FAILURES=1000 mvn spring-boot:run\n` +
        `  cd web && npm run dev`,
      // Kept rather than flattened into the message, so the original network error survives in the
      // chain for anyone who needs it.
      { cause },
    );
  }
}

/**
 * Checks the suite has not already used up this address's login allowance.
 *
 * This is worth a dedicated check because the failure is so misleading otherwise. Once the IP throttle
 * engages, every login is refused before credentials are even looked at — so tests start failing from
 * whichever one happens to come next, with symptoms ("ended up on /login") that point at routing or
 * session handling rather than at a rate limit. Asking once, up front, turns a cascade of plausible
 * wrong answers into one accurate sentence.
 */
async function requireLoginAllowance() {
  const csrf = await fetch(`${API_BASE_URL}/api/auth/csrf`, { credentials: "include" });
  const { headerName, token } = (await csrf.json()) as { headerName: string; token: string };
  const cookies = csrf.headers.getSetCookie?.() ?? [];

  const probe = await fetch(`${API_BASE_URL}/api/auth/login`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      [headerName]: token,
      Cookie: cookies.map((cookie) => cookie.split(";")[0]).join("; "),
    },
    body: JSON.stringify({
      username: "throttle-probe-account-that-does-not-exist",
      password: "throttle-probe-password",
    }),
  });

  if (probe.status === 429) {
    throw new Error(
      "The API is throttling logins from this address, so these tests cannot run.\n" +
        "That is the rate limiter working, not a bug. Restart the API with a ceiling high enough\n" +
        "for a test suite, which is what the Spring integration tests do too:\n" +
        "  cd api && APP_THROTTLE_MAX_FAILURES=1000 mvn spring-boot:run\n" +
        "Or wait for the throttle window (15 minutes by default) to pass.",
    );
  }
}

export default async function globalSetup() {
  await requireReachable("The frontend dev server", WEB_BASE_URL);
  await requireReachable("The API", `${API_BASE_URL}/api/auth/csrf`);
  await requireLoginAllowance();
}
