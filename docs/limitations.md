# Documented limitations

Story 1.24's last acceptance criterion: these are **recorded rather than tested**, because a test would
have to assert the limitation is still there — which pins the wrong thing and would fail the day someone
fixed it.

Each entry says what the limitation is, why it was accepted, and what it would take to remove.

---

## 1. Rate-limit counters are in-memory

`RateLimitFilter` keeps its three counters (per-account login, per-IP login, per-IP reset) in Caffeine
caches inside the JVM.

**Consequences, stated plainly:**

- **Not restart-durable.** A restart clears every counter. An attacker who can trigger a restart, or who
  simply waits for a deploy, gets a fresh budget.
- **Not multi-instance-safe.** Two instances behind a load balancer each enforce the configured limit
  independently, so the effective limit is `n × configured`.

**Why accepted.** Ticket 02 scoped the counters this way deliberately. The durable, security-critical
counter is the **third** one — account lockout — which lives in `users.failed_login_attempts` and
`users.locked_until` and is unaffected by either point above. The two in-memory counters are throttles
in front of it, not the lockout itself, so a cleared throttle does not clear a lock.

**What removing it takes.** A shared store (Redis, or a database table with the same TTL semantics)
behind the `Cache<String, Bucket>` seam. The filter's structure does not change; only the backing map
does.

**Note for tests.** `RateLimitFilter.resetCounters()` exists solely so the integration suite does not
starve itself — the login limit is 50 a minute from one IP and the suite makes far more than that from
`127.0.0.1`. Nothing in the application calls it: a counter that could be cleared at runtime would not be
a rate limit.

---

## 2. The application is single-instance only

Beyond the counters above, one further mechanism assumes a single process:

- **The absolute session timeout** is enforced by `AbsoluteSessionTimeoutFilter` against the injectable
  `Clock`. This is safe across instances (every instance reads the same wall clock and the same session
  creation time from Spring Session), so it is *not* a limitation — noted here only because it is the kind
  of thing that looks like one.

What genuinely is single-instance is the rate limiting above. Session state itself is **not**: sessions
live in `SPRING_SESSION` via Spring Session JDBC precisely so that the one-session-per-account limit holds
across requests and restarts (`Std:409`), which `SessionRegistryImpl` would have silently broken.

**What removing it takes.** Item 1's shared store. There is no second piece of work.

---

## 3. The `Secure` cookie on `http://localhost` is verified server-side only

`server.servlet.session.cookie.secure` is `true` in **every** profile, including local development over
plain HTTP (ticket 09). That rests on browsers treating `localhost` as a trustworthy origin and returning
the cookie anyway.

**What is verified, and how:**

| Claim | Verified by | Status |
|---|---|---|
| The server emits `Secure; HttpOnly; SameSite=Lax; Path=/` over plain HTTP | `SessionCookieIT`, and `curl -i` against the running dev profile | **verified** |
| The server accepts the cookie when a client returns it | the whole full-HTTP integration suite | **verified** |
| A *browser* returns a `Secure` cookie to `http://localhost` | nothing in this repository | **not verified here** |

The third row cannot be closed by an HTTP client: a client has no `Secure`-attribute policy to violate,
so a green suite proves nothing about it. It belongs to the `browser-test` acceptance gate.

**If it turns out to be false**, ticket 09 recorded the fix in advance: a one-line `dev`-only override
setting `secure: false`. No other profile changes, because every other profile is served over HTTPS.

---

## 4. There is no alerting channel

`logback-spring.xml` attaches a `statusListener` so Logback reports appender failures on the console,
which is the *detection* half of `Std:262`. There is no notification half: an operator has to be watching
stdout.

**Why accepted.** No alerting platform is in scope, and ticket 05 established that logging conformance
needs no external collector. Claiming the constraint discharged because a listener exists would be the
dishonest version of this entry.

---

## 5. Reset links are not emailed

There is no mail server. `EmailService` emits a structured audit event carrying **neither the token nor
the link**, and the link itself goes to a `ResetLinkChannel`:

- outside `dev`: the discarding implementation — the link is rendered nowhere
- in `dev` only: `DevConsoleResetLinkChannel`, which prints to the console

That console write is the single documented exemption from ArchUnit rule 1, named explicitly in
`ArchitectureRulesTest.CONSOLE_EXEMPT_CLASS` and guarded by a second test asserting the class stays
`@Profile`-gated. Widening it means editing the rule, in a diff, which is the point.

**Consequence.** In a deployed profile a self-service reset cannot complete without an administrator
issuing the token out of band (`PATCH /users/{id}/resetPassword` returns it to the administrator). Wiring
a real mail sender is the only work needed; the seam is already there.

---

## 6. `dependency-check-maven` has not been run

The plugin is configured in the non-default `owasp` profile with `failBuildOnCVSS=7`, per spec.md S1
and S12. It has **not been executed to completion**, and the reason is environmental rather than a
decision.

`mvn -Powasp dependency-check:check` was attempted. The profile and the plugin resolve correctly — this
is not a configuration fault — and it then reported:

```
[INFO] Checking for updates
[WARNING] An NVD API Key was not provided - it is highly recommended to use an NVD API key
          as the update can take a VERY long time without an API Key
[INFO] NVD API has 399,572 records in this update
```

It was still downloading those 399,572 records when the attempt was cut off at four minutes. So the two
operational notes the plan predicted are confirmed rather than assumed: it needs an `NVD_API_KEY`
(documented in `.env.example`), and a cold cache makes the first run very slow.

Run `mvn -Powasp verify` once a key is available.

### Where definition-of-done list B actually stands

For the avoidance of a false impression — the per-slice list (list A) is green, but list B is not finished:

| # | Gate | Status |
|---|---|---|
| 5 | `im8-review` clean | **not run** |
| 6 | `dependency-check-maven`, no CVSS ≥ 7 | **not run** — see above |
| 7 | `browser-test` against the PRD's twelve stories, including the three-step first boot | **not run** — the flow was verified over HTTP against the running dev profile, which is not the same thing as a browser |
| 8 | All ArchUnit rows green | **green** — six tests in `ArchitectureRulesTest` |
