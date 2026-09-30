# Secured Hello World

A reference authentication application: a React single-page app and a Spring Boot API on separate origins, with
username-and-password sign-in, a second factor for administrators, lockout and throttling, credential recovery and
admin user management. This file is the project's glossary. It defines words, not behaviour.

## Accounts and identity

**Pending registration**:
An account record holding a reserved username and an email address but no password, created by self-registration
or by an admin invite. It becomes usable only when its activation token is redeemed. A self-registered one lapses
24 hours after its last registration.
_Avoid_: unverified account, unconfirmed user

**Username hold**:
A registration's claim on its username for 24 hours, taken whatever state the email address is in, so that no other
address can register that username meanwhile.
_Avoid_: reservation (unqualified), username lock

**Account disable**:
An administrator's reversible decision that an account may not sign in. It is independent of activation: an account
signs in only when it is both activated and not disabled.
_Avoid_: disabled (unqualified), deactivated, suspended

**Canonical identifier**:
The one normalised form of a username or email address (Unicode NFC, trimmed, lower-cased, with no dot or `+tag`
folding) that is stored and compared everywhere, tombstones included. A non-canonical username is rejected; an email
address is converted.

**Deleted-user tombstone**:
The permanent record left when an account is deleted. It keeps the username and a keyed hash of the email address so
that neither can ever be registered again, and it is pseudonymised, not anonymised.
_Avoid_: soft-deleted user, archived account

**Role definition**:
The fixed set of roles that exist, USER and ADMIN, declared in configuration and never changed while the application
runs. A role assignment is different: it is which role one account holds, and an administrator may change it.

**Authorization matrix**:
The declared mapping from each role to the method-and-path pairs it may reach. It is the single source of which role
reaches which route. Public paths are checked first, role rules second, and everything else is denied.

**Actor**:
The administrator performing an admin action. An actor may never act on themselves to disable, demote, delete, unlock
or reset a factor.

**Subject**:
The account an admin action is performed on.
_Avoid_: managed user, target user

**Enrolled admin**:
An ADMIN account that is activated and has a confirmed second factor. The rule that at least two admins must remain
counts only enrolled admins, so an unredeemed invite never counts.

**Authenticable**:
Whether an admin can currently complete sign-in with both factors: enabled, activated, password not disabled, factor
enrolled, and factor not under tier-2 disable. Which admin recovery route applies depends on whether another
authenticable admin exists.

## Credentials and tokens

**Credential token**:
A single-use, expiring secret that sets a credential when redeemed and never creates a session. It is stored only as
a hash. Activation and password reset are its two types, and issuing one cancels the account's earlier unredeemed
tokens of the same type.

**Activation token**:
The credential token whose redemption sets an account's first password and activates it.
_Avoid_: verification token, confirmation link

**Invite token**:
An activation token that an administrator creating an account receives once, to pass on, instead of it being emailed.
It is not a separate token type.
_Avoid_: temporary password, generated password

**Admin-issued token**:
An invite token or a reset token an administrator was returned, marked as such on the token. The marker, never the
account's role, is what tells an invite from a self-registration, and a self-service request never replaces a pending
one.
_Avoid_: admin token, invite flag

**Domain-separated token hash**:
A token's stored form, hashed together with its type label so that a token of one type can never be redeemed as
another.

**Link origin**:
The scheme and host used to build activation and reset links. It comes only from deployment configuration and never
from anything in a request.

**Forced-change credential**:
A password the account must replace at its next sign-in before doing anything beyond a small fixed set of actions. It
arises from the bootstrap administrator seed, a re-enable, a tier-2 disable, or an operator's out-of-band rebind. A
seeded or re-enabled credential expires if it is still unchanged after 30 days.

**Authentication pathway**:
A route that grants an authentication factor to a session. There are three: password sign-in, second-factor
enrolment confirmation, and second-factor verification.

**Credential-setting route**:
A route that sets a credential without granting a factor or creating a session. These are redemption of an activation,
invite or reset token, completion of a forced change, the bootstrap administrator seed, and an operator's out-of-band
rebind. Such a route is not an authentication pathway.

**Rebinding**:
Recovering a disabled authenticator by destroying the old credential and binding a new one, the only way out of an
authenticator disable (NIST SP 800-63B-4 §3.2.2). An unlock is never rebinding, because it destroys no credential.
_Avoid_: unlock, re-enable

**Password rebinding**:
Rebinding a password, either by redeeming a reset token or by an operator setting a new password out of band.

**Factor rebinding**:
Rebinding an admin's second factor: another administrator resets it, which deletes it, and the admin then enrols
again. It is the only way out of terminal factor state.

## Sessions

**Auth instant**:
The moment of successful password sign-in, recorded once per session. The absolute session lifetime is measured from
it. It is not the session's creation time, and factor grants and step-ups never move it.

**Absolute session lifetime**:
The maximum age of a signed-in session, measured from the auth instant and never extended by activity. Distinct from
the idle timeout, which activity does extend.
_Avoid_: absolute session timeout, session expiry (unqualified)

**Superseded session**:
A session ended because a newer event replaced it, such as a newer sign-in by the same account or a credential change,
rather than because a clock ran out. Anything bound to it, including its CSRF token, is rejected.
_Avoid_: expired session, which means a timer ran out

**Anonymous session**:
A session with no auth instant. It exists only to hold a pre-sign-in CSRF token and is created by exactly one route.

**Pinned expiry**:
An anonymous session's expiry, fixed at its creation time plus the idle interval and never extended by traffic.
_Avoid_: pin (unqualified)

**Shed episode**:
A period in which new anonymous sessions are refused, because the live session count or free disk space has crossed
its limit.
_Avoid_: throttling, which is per source

**Reserve**:
The free disk space a shed episode protects. It scales with the number of live session rows, not with the size of the
database file.

**Reconciliation sweep**:
The idempotent pass at startup that ends the sessions of every account whose state should already have ended them. It
repairs session kills lost to a crash between committing the state change and killing the sessions.

**Session-store miss**:
A request that presents a session identifier the session store cannot resolve.

**Miss budget**:
The per-source allowance of session-store misses, applied on every route. It bounds a database cost that no
per-route budget sees.

## Second factor

**Pending enrolment**:
A second-factor secret an admin has been shown but has not yet confirmed with a valid code. It expires, and
provisioning again replaces it.

**Enrolment binding**:
Confirming a pending enrolment with a valid code. This one step binds the authenticator to the account and grants the
factor. It is distinct from provisioning, which only creates a pending enrolment, and from factor rebinding.

**Factor freshness**:
How long ago the session's second factor was granted. Admin reads accept a factor of any age within the session's
life, while admin mutations need one inside the re-verification window.

**Re-verification window**:
The maximum factor freshness an admin mutation accepts, which is ten minutes. Past it, the admin must step up.

**Step-up**:
Entering a second-factor code again within an existing session to renew factor freshness. It does not move the auth
instant.

**Step-up queue**:
The client's list of admin requests refused because the factor was stale. After one successful step-up they are all
replayed, and on a terminal refusal they are dropped. The server keeps no copy.

**Tier-1 lock**:
A temporary lock on an admin's second factor after repeated consecutive wrong codes. It lifts on its own, resets on
success, and an admin unlock clears it.
_Avoid_: tier 1 (unqualified), which also names a keying tier

**Tier-2 disable**:
The authenticator disable of an admin's second factor after 100 cumulative wrong codes. Success never resets the
count, factor rebinding is the only way out, and it also forces a password change at the next sign-in. It uses the
same number as the cap counter, but counts differently.
_Avoid_: tier 2 (unqualified), factor lockout

**Terminal factor state**:
The state of an admin whose factor is under tier-2 disable: still enrolled, but unable to succeed with any code. The
client shows this state instead of offering a challenge.

**Context prefix**:
The owner-and-key-version header sealed inside each encrypted second-factor secret and checked after decryption. A
secret moved to another account, or read under the wrong key, is caught as a security event rather than a decryption
error.

**One-time secret display**:
Showing an invite token, an admin-issued reset token or a new factor secret exactly once. The secret is never logged,
cached or fetched again.

## Failure limits

**Lockout**:
A temporary refusal of password sign-in on one account after consecutive failures inside the observation window. It
lifts on its own, after a duration that grows as the account nears the cap.
_Avoid_: account lockout, suspension

**Observation window**:
The period within which password failures must fall to count as consecutive toward a lockout.

**Failed-login counter**:
The number of consecutive password failures inside the observation window. It drives lockout and resets when the
window lapses.
_Avoid_: failed login attempts (unqualified)

**Cap counter**:
The number of consecutive password failures since the last successful password sign-in. It never resets with time,
and it drives the password's authenticator disable at 100 (NIST SP 800-63B-4 §3.2.2). Tier-2 disable also uses 100,
but counts cumulative factor failures that success never resets.

**Authenticator disable**:
An automatic state on one of an account's authenticators, the password or the second factor, that never lifts on its
own and ends only by rebinding. No administrator action sets or clears it.
_Avoid_: disable (unqualified), account disable

**Throttle**:
A refusal keyed on a source key or on a submitted value. It is held in memory, answered with 429, and leaves no state
on any account.
_Avoid_: lockout

**Cardinality axis**:
The throttle on how many distinct accounts one source key has driven into lockout within an hour. Past the limit,
that source is refused for any username not already among them.

## Sources

**Client IP**:
The caller's resolved address: the socket peer, unless a named trusted proxy is configured. The source key is derived
from it, and it is never used as a key itself.
_Avoid_: source IP

**Source key**:
The value every per-source control and log field treats as "a source": an IPv4 address, or an IPv6 address masked to
the configured prefix, tagged with its family. It identifies a network, not a host.

**Untracked source**:
A source key beyond the distinct-source cap in a keying window. Its occurrences are counted but not recorded
individually.

## Audit and observability

**Audit row**:
One entry in the numbered catalogue of security events written to the dedicated audit stream. A keyed row can stand
for many occurrences.

**Event discriminator**:
What tells two operations apart in the audit stream when their action and type are too coarse: the static message
plus a precise event type.
_Avoid_: discriminator (unqualified)

**Reason family**:
The closed set of reason values allowed on one event, so a reason from another family cannot be recorded against it.
The API error contract's per-family reasons are the same vocabulary.

**Identity-absent failure ratio**:
For one source key, the share of failed sign-ins whose submitted username matched no account. A high ratio is the
signature of username enumeration.

**Pre-handler row**:
An audit row written before the request is matched to a route, so its path is text the client supplied. It is the
only kind of row that can carry log injection.

**Transition-keyed row**:
An audit row written once per change of state, such as a lock engaging or a budget running out, rather than once per
refused request.

**Keyed row**:
An audit row written at most once per key and keying window. It carries the count of occurrences it stands for and
when the first was seen.

**Keying tier**:
A class of keyed rows, grouped by whether an attacker can grow the key space. Tier 1 is keyed by source key, which an
attacker can grow; tier 2 is keyed by account, which is bounded. The tiers are not severities.
_Avoid_: tier 1 or tier 2 (unqualified)

**Keying window**:
The one window over which keyed rows aggregate and the distinct-key caps reset.

**Truncation row**:
The single aggregate row written when a keying tier reaches its distinct-key cap within a window. Nothing more is
written for that tier in that window.

**Alert class**:
The class of an audit row, or of one of its reasons: per-event, rate-above or rate-below. The class depends on whether
clearing the reported state needs action outside the normal flow.

**Per-event alert**:
An alert on a single occurrence, because clearing the state it reports needs a person.

**Rate-above**:
An alert on a spike in rows whose states clear themselves. The deployer sets the thresholds.

**Rate-below**:
An alert on a drop in log throughput, or its absence, raised outside the logging channel it watches.

**Observability boundary**:
The line between what the application emits about itself and what only an outside observer can do, such as alerting,
computing rates and detecting silence. Everything beyond the line is a deployer obligation.

**Trace restart**:
Discarding all trace context the caller supplies at the application boundary, so that every request begins a new
trace generated by the server.
_Avoid_: trace validation, trace continuation

## Browser and origins

**Same-site**:
Two origins with the same scheme and registrable domain, such as the SPA and API on two ports of one host. They can be
cross-origin and still same-site. Cookie `SameSite` rules follow the site, while CORS and the same-origin policy follow
the origin.

**Document context**:
The browsing context that loads the SPA's HTML and scripts. It is the only place a content security policy has any
effect.

**API origin**:
The origin that serves only JSON and never a document, so a policy on its responses protects no page.

**Belief versus authority**:
The client's cached view of its own sign-in, role and factor state is belief, and it decides where the app may
navigate. The error code on a response is authority, and it overrides belief every time.

**Visual-integrity degradation**:
The failure where a blocked cosmetic stylesheet leaves a control working but wrongly styled. It is distinct from a
broken control.

## Configuration and deployment

**Prohibited configuration**:
A setting that is well-formed and binds correctly, but that has been argued must never be set. Invalid configuration
is different, because it fails to bind.

**Blinding key**:
A key that makes a pseudonymised lookup index searchable by equality. It protects no secret: leaking it reveals only
whether a given value was ever present.

**Seam register**:
The closed list of every point where the schema cannot be written the same way for H2, PostgreSQL and MySQL. Each entry
is checked by review and never executed.

**Acceptance check**:
How a reader proves that a deployer obligation was carried out. It is distinct from a check the application enforces
itself.

**Declared vacancy**:
A named deployment role with no holder, recorded as an unmet acceptance check.

## Testing

**Canary secret**:
A known secret that the test suite plants, or captures from a response, and then searches for in every output. Finding
it proves a leak.

**Isolation class**:
How a test avoids interference from shared state: keyed, delta, own database, or merged.

**Context configuration**:
One of the fixed, named application contexts that every security-control test runs in.
