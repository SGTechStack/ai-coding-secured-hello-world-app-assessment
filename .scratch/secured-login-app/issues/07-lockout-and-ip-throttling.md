# 07 — Account lockout and IP throttling

Type: grilling
Status: open
Blocked by: 02
Map: [Secured Login App](../map.md)

## Question

What is the account lockout policy, and by what mechanism is IP-based rate limiting enforced?

Answer `Q15` (account lockout policy and admin unlock capability) and `Q16` (IP-based rate limiting strategy) of the standard's question set.

The PRD's Story 3 sets the shape and names the reason it matters: **account lockout and IP throttling must be independent**, "so an attacker cannot lock out a legitimate user merely by failing that user's password from one source" (`prd/assessment-prd.md:56`). Two counters, two policies.

- **Account lockout** — the PRD suggests 5 attempts / 15 minutes and gives `failed_login_attempts` + `locked_until` columns. Confirm or override the thresholds, decide whether the attempt window is sliding or absolute, and settle `Q15`'s **admin unlock** capability, which the PRD never mentions (no unlock endpoint exists in Stories 8–11 — if unlock is required, that is a new endpoint and a new authorization-matrix row for 08).
- **IP throttling** — the PRD specifies behaviour but no mechanism. Decide the store (in-memory versus shared, which matters only if more than one instance ever runs), the library or hand-rolled filter, the threshold and window, the response (429 versus the generic login error), and where it sits in the Spring Security filter chain. Note that the enumeration-resistance requirement constrains what the throttled response may reveal.

Blocked on 02 because the counter store and the session/datasource decision are the same decision for the IP limiter.
