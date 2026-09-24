# 09 — Decide lockout and dual rate limiting semantics

Type: grilling
Status: open
Blocked by: 01, 02, 04, 06

## Question

Exactly when does an account lock, exactly when does an IP get throttled, and how do the two
interact at every edge?

## Settled going in

**Both limiters, independently.** The PRD (Story 3) demands per-IP throttling so an attacker cannot
lock out a legitimate user from a single source. The App Standard demands per-account counters and
explicitly argues per-IP is bypassable via IP rotation — while its own sequence diagram shows a
per-IP check, so the standard contradicts itself. Both arguments are correct about different
attacks, so we implement both. Bucket4j + Caffeine behind a `LoginRateLimiter` abstraction, in
memory, single instance.

## What to decide

**Account lockout.**

- Threshold: PRD 5, standard 5. Agreed.
- Duration: PRD says 15 minutes, standard default 20. Value conflict — standard wins per the map's
  conflict rule, but confirm.
- Is the counter *consecutive* failures, or failures within a rolling window? The PRD says both
  things in different places ("N consecutive failed attempts" and "within a window"). Decide one.
- Automatic lift on expiry, plus admin unlock. The standard also says an admin-initiated password
  reset does **not** clear the lock. Confirm.
- Counter persistence: must survive restart (the standard lists in-memory lockout state as a failure
  source), so it lives on the user row — which is what the PRD's data model already implies.
- Does a correct password against a locked account extend the lockout or leave it alone? PRD Story 2
  says it is still rejected; it does not say whether the clock resets. Decide, and note that
  extending it creates a denial-of-service lever.

**The edges that leak information or cause bugs.**

- **Unknown username**: does it increment anything? There is no account to count against. Decide
  whether unknown usernames feed only the IP limiter. This interacts with the timing mitigation in
  "Decide the API error envelope".
- **Disabled account with correct password**: does the failed counter increment?
- **Non-verified account** (from the registration gating decision): same question.
- Does the lockout response differ from a wrong-password response? It must not — the standard
  requires `account locked` to be indistinguishable from invalid credentials on the wire.

**IP throttling.**

- Budget and window. The standard's per-account budget is 10/minute; the per-IP budget is ours to
  set. Consider that a shared corporate NAT puts many legitimate users behind one IP — set a budget
  that doesn't lock out an office.
- Response: 429 with `Retry-After` per the standard.
- Scope: login only, or also password-reset request and token redemption? The standard requires rate
  limiting on the reset endpoints too.
- **`X-Forwarded-For` trust.** Deployment topology is out of scope, so there is no known proxy to
  trust. Default to the direct socket address and treat XFF as untrusted, because trusting a
  spoofable header turns the limiter into a no-op. Decide how the trusted-proxy configuration is
  exposed for whoever does deploy this, and make the insecure default impossible.
- Eviction and memory bound on the Caffeine cache — an unbounded key space keyed by attacker-chosen
  IPs is itself a denial-of-service vector.

**Interaction.** Which check runs first, and does an IP-throttled request still increment the
account counter? If it does, an attacker can lock any account by deliberately tripping their own IP
limit — exactly the attack the PRD wants prevented. Get this ordering right; it is the crux.

## Done when

Every edge above has a stated behaviour, the ordering question is resolved, and the XFF default is
recorded along with how a deployer overrides it safely.
