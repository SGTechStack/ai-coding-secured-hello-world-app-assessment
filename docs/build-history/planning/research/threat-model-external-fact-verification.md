# Verification asset — ticket 15 (threat model)

Checked 27 September 2026. Produced under the map's **verification rule**: an argument that rests on an
external fact is not recordable until that fact is checked against a primary source. "Primary" here means a
W3C Recommendation, official framework reference documentation, or published javadoc — not StackOverflow, not
a blog. Every claim below is either **VERIFIED**, **PARTIALLY VERIFIED** (with the unverified half named), or
**NOT VERIFIED** (and therefore not used to carry an argument in the ticket).

Five of the eleven external facts checked during ticket 09's reopening were wrong, "every one of them in the
direction that favoured the conclusion being argued". The same bias check was applied here: §2 below is the
finding I most wanted to be true, and it is the one that came back *partially*.

---

## §1 — Spring Security filter ordering: exploit protection precedes authentication and authorization

**VERIFIED.** Source: [Spring Security reference, Servlet Architecture](https://docs.spring.io/spring-security/reference/servlet/architecture.html).

The page lists the ordering produced by a `csrf` + `httpBasic` + `formLogin` + `authorizeHttpRequests`
configuration as `CsrfFilter` → `BasicAuthenticationFilter` → `UsernamePasswordAuthenticationFilter` →
`AuthorizationFilter`, and states the sequence in prose: the CSRF filter runs first to protect against CSRF
attacks, then the authentication filters, then `AuthorizationFilter` authorizes the request. Its "Adding a
Custom Filter" section states the same thing as an ordered list of key events — the security context is loaded
from the session, then the request is protected from common exploits (secure headers, CORS, CSRF), then the
request is authenticated, then it is authorized.

The printed default chain on the same page is
`DisableEncodeUrlFilter, WebAsyncManagerIntegrationFilter, SecurityContextHolderFilter, HeaderWriterFilter,
CsrfFilter, LogoutFilter, UsernamePasswordAuthenticationFilter, …, ExceptionTranslationFilter,
AuthorizationFilter`.

**Why it is load-bearing.** It establishes that a CSRF rejection is evaluated **before any authorization
decision and before handler mapping**, for *every* request path — including paths that match no controller.
That is the mechanism behind finding TM-01: a control that runs ahead of routing produces its audit row on
paths the route-keyed rate limiter has never heard of.

Corroborated in-repository by three tickets independently: ticket 08 requires the absolute-session filter
"after `SecurityContextHolderFilter` and before `CsrfFilter`" precisely so an expired session does not get "a
misleading 403 … before the 401" (`08:412-420`); ticket 11 registers the forced-change filter
`addFilterBefore(..., AuthorizationFilter.class)` (`11:351-352`); ticket 23 registers the factor filters
"after `CsrfFilter` … and after ticket 11's forced-change filter, which shares the anchor" (`23:350-355`).

## §2 — Session-attribute deserialisation: the filter hook exists, the default is unconfigured

**PARTIALLY VERIFIED.** Sources:
[`DeserializingConverter` javadoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/core/serializer/support/DeserializingConverter.html),
[`DefaultDeserializer` javadoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/core/serializer/DefaultDeserializer.html)
(both Spring Framework 7.0.9).

What the javadoc does say:

- `DeserializingConverter` has three constructors. The no-argument one is documented as creating a converter
  "with default `ObjectInputStream` configuration, using the 'latest user-defined `ClassLoader`'". The others
  take a `ClassLoader` or a `Deserializer` delegate.
- `DefaultDeserializer` mirrors that, and **exposes a `getObjectInputFilter()` method** — so a JDK
  deserialisation filter is a first-class, settable property on this path in Spring Framework 7.

What I **could not** confirm from a primary source, and therefore do not assert:

- That Spring Session JDBC's default `ConversionService` installs **no** filter. The
  [Spring Session JDBC configuration page](https://docs.spring.io/spring-session/reference/configuration/jdbc.html)
  documents the table names and schema but returns no match for serialization or conversion-service
  configuration, so the default is not stated in the reference documentation. `DeserializingConverter`'s
  no-arg constructor being documented as "default `ObjectInputStream` configuration" is *consistent with* no
  filter, and is not a statement that there is none.
- The exact default value returned by `DefaultDeserializer.getObjectInputFilter()`. The rendered javadoc
  method detail was not retrievable.

**Consequence for the ticket, stated at the strength the evidence supports.** The recordable claim is the
narrow one: *the decided design stores `SPRING_SESSION_ATTRIBUTES.ATTRIBUTE_BYTES` as JDK-serialised bytes
(ticket 05 chose this deliberately, `05:245-251`), deserialises them on every authenticated request, and no
ticket on this map states whether a deserialisation filter is configured.* Whether the framework default is
"no filter" is a **first-implementation check handed to `/do-work`**, not a fact this ticket asserts — which
is also why the finding lands as a build-phase control plus a handover item rather than as a reopening.

I deliberately did **not** write the sharper version ("the design deserialises attacker-influenceable bytes
with no allowlist"), because the sharper version needs the fact I could not verify, and it is the version that
favours the conclusion.

## §3 — W3C Trace Context: inbound `traceparent` is adopted, and forging it is a named attack

**VERIFIED.** Source: [W3C Trace Context, Recommendation](https://www.w3.org/TR/trace-context/) (editorially
updated since the 6 February 2020 Recommendation).

Three sections carry the finding.

- **§4.3, "A `traceparent` is Received"** — the processing model is explicit that a received `traceparent` is
  *used*: the vendor parses the version, validates `trace-id` and `parent-id`, and only "creates a new
  `traceparent` header and deletes `tracestate`" **if** parsing or validation fails. A well-formed inbound
  `trace-id` is therefore adopted as the trace identity by a conforming implementation. §4.2 confirms the
  converse: a new `trace-id` is generated only when no `traceparent` was received.
- **§7.2, "Denial of Service"** — content rephrased for compliance with licensing restrictions: where
  distributed tracing is switched on for a publicly reachable API and any incoming trace is continued without
  question, the specification warns that an attacker can load the application with tracing work, **contrive
  `trace-id` collisions that render the monitoring data useless**, and drive up costs at a hosted tracing
  provider. It puts the obligation on implementers to have checks in place against denial *of monitoring* by
  malicious or poorly written callers, and offers as an example protection **treating authenticated and
  unauthenticated requests differently**, plus rate limiters on data recording.
- **§7, preamble** — anyone relying on these headers is told to apply normal defensive parsing practice to
  them, header length and value content included, the named reasons being buffer overflow and HTML injection.
  In other words the specification classifies `traceparent`/`tracestate` as untrusted input.

Two adjacent sections were checked and are **not** in play for us, recorded so they are not cited by mistake:
§6.3 and §7.3 concern `traceparent`/`tracestate` appearing in *responses* and reaching cross-origin callers —
our API sets no such response header and exposes none through CORS, so that limb does not apply.

**Why it is load-bearing.** Ticket 13 made `trace.id` the **join key** of the authentication correlation
chain: row 8 carries the pre-rotation `session.hash`, row 1 the post-rotation one, and they are joinable only
because "rotation happens inside the login request, so … **share one `trace.id`**" (`08:597-603`,
`13:443-450`). Ticket 21 keeps the audit listener synchronous specifically so MDC — `trace.id`,
`session.hash`, `user.id` — survives (`13:575-577`). Ticket 03 adopted `spring-boot-starter-opentelemetry`
*for* `trace.id` (`03:275`, `03:307`). Nothing on the map asks where `trace.id` comes from on an inbound
request. Under §4.3 it comes from the caller, and under §7.2 forging it to collide is a named attack on
exactly the property ticket 13 built.

**Not verified, and therefore not asserted:** the precise default propagation configuration of Boot 4.1 with
`spring-boot-starter-opentelemetry` — i.e. that W3C extraction is on by default and cannot be switched off
without also losing outbound propagation. Searching returned only secondary sources (StackOverflow), which
this asset does not accept. The recordable claim is standards-level and framework-agnostic: *a conforming
trace-context implementation adopts a valid inbound `trace-id`, and the specification itself names forged
collisions as an attack.* Confirming Boot 4.1's default propagator and the available restart mechanism is
**ticket 27's own research obligation**, and ticket 27 must not assume the answer is "on by default", however
likely that is.

## §4 — Repository facts, checked by reading rather than remembering

Per the map's extension of the verification rule to facts about this repository, these were read at source
rather than recalled.

| Claim | Status | Where |
|---|---|---|
| The per-IP limiter's budget table is a **route allowlist**; no ticket states a default for a route absent from it | VERIFIED | `09:307-318` (ten rows, each naming a route), `09:749-758` (ticket 23 adds three more rows) |
| Admin endpoints are deliberately unthrottled | VERIFIED | `09:358-360`, restated `09:757-758` |
| `GET /api/hello` has **no** authorization-matrix row, **no** rate-limit row, and is deliberately unaudited | VERIFIED | Only three mentions exist anywhere in `issues/`: `03:239` and `13:254-257` (both "do not log"), and `11:192` (cited only to fix the unversioned base path). It appears in no endpoint table, no matrix, and no budget table. |
| `anyRequest().denyAll()` is last, so an unlisted endpoint fails closed | VERIFIED | `11:177-181` |
| Audit rows 12, 13 and 14 sit in the **rate-above** alert class and are not transition-keyed | VERIFIED | `21:404-442` |
| Row 46's truncation cap bounds only `RATE_LIMITED_SOURCE` breach rows | VERIFIED | `13:728-731`, `13:722-727` |
| Row 46's `N` has **no value, no property key and no named binding test** anywhere | VERIFIED | `13:729`, `13:734`; `21:574-585` commits only to the general seam rule; `21:515-517` puts the table in unquoted working notes |
| The tier-2 TOTP trip writes `totp_user_details` **and** `users.force_password_change` | VERIFIED | `23:1042-1057`, `11:820-833` |
| The pinned lock order is user rows → TOTP rows → session rows | VERIFIED | `09:1324-1327`, `23:565-567`, `11:586-589` |
| The factor requirement exists **only** in `authorizeHttpRequests`; `@PreAuthorize` carries role only | VERIFIED | `23:240-244` (the annotation abandoned precisely because it would AND the factor into method security too), `11:177-181` (`@PreAuthorize` as role defence in depth) |
| `deleted_users` retains `user_id`, `username`, `email_hmac`, `deleted_at`, `deleted_by_id` — and **no role** | VERIFIED | `12:562-582` |
| Ticket 25 is `Status: claimed` by another session | VERIFIED | `25:4` — which is why every handover obligation from this ticket is declared under the map's extraction heading rather than written into 25 |
