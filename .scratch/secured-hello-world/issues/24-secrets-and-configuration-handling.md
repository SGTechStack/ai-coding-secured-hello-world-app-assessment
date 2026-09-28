# 24 — Decide secrets and configuration handling for non-local environments

Type: grilling
Status: resolved
Blocked by: 11, 20

## Question

Every secret this application needs: where does it come from, what happens when it is absent, and what
does whoever deploys this have to be told?

## Why this is a ticket now

It sat in the map's **Not yet specified** as a patch that had "grown enough that it may graduate into
its own ticket once 19 resolves". It did.
[Resolve the MFA scope conflict raised by IM8 ac-2](19-mfa-scope-conflict.md) added a second hard
requirement — a TOTP secret encryption key that must exist, must not live in the database, and must be
rotated yearly — which pushed this past the point where it could be decided incidentally inside another
ticket.

## The inventory to settle

- **TOTP secret encryption key.** Primitive already decided (AES-GCM, `AesGcmBytesEncryptor
  .withSecretKey`, environment-supplied, key-version column for incremental rotation). What remains:
  property name, encoding, length validation, fail-fast behaviour when absent or malformed, and the
  written rotation procedure. IM8 **as-8** requires it absent from every profile.
- **Admin bootstrap credentials.** Ticket 11 decides whether the seed is profile-gated and whether the
  seeded admin must change password on first login. This ticket decides how the credential arrives and
  what the application does when it is unset, default, or weak — refusing to start being the likely
  answer, since "shipped with a known bootstrap password" is the hole being closed.
- **Datasource credentials.** H2 locally, but the configuration must not hardcode a path that breaks the
  moment someone points it at a real database.
- **Session cookie name per profile.** Ticket 05 found `__Host-` forces a per-profile name because the
  prefix requires `Secure`. Decide the names and where they are set.
- **Any session `hash` HMAC key**, if ticket 08's session contract ends up needing one.
- **The reverse-proxy or two-origin configuration** from ticket 20, to the extent it carries hostnames
  or allowed origins that differ per environment.

## Inherited from ticket 07 — a constraint on the rotation policy

[Decide the password policy and hashing parameters](07-password-policy-and-hashing.md) considered peppering the
password hash under this ticket's key facility and **declined**, for a reason this ticket has to carry:

- **A peppered password hash cannot be rotated.** The TOTP secret can — decrypt under the old key, re-encrypt
  under the new one, no user involvement. A password hash peppered under key *K* cannot be re-derived under *K'*
  without the plaintext, which we do not hold and must never hold. Forcing a global reset is not an escape:
  NIST SP 800-63B-4 §3.1.1.2 forbids requiring periodic password change.
- So **this ticket's rotation policy must scope itself to keys that can rotate**, and state explicitly that no
  password-hash pepper exists and why. Left implicit, the policy reads as covering something it cannot cover,
  and whoever implements rotation later will go looking for a pepper that was deliberately never created. *Consolidated into the register (ticket 33): R-CFG-010. Amend the table by ID, not this list.*
- Worth recording that ticket 02 originally deferred the pepper as "blocked on unresolved secrets handling".
  **That reason expired when this ticket was created.** The facility now exists; the decision stands on
  rotation grounds instead. Anyone revisiting it should not resurrect the stale justification.
- Nothing in the password policy adds a secret to the inventory: the blocklist is a checked-in classpath
  resource, and there is no pepper, no HMAC key and no third-party API credential on the password path.

## What to decide

- **Mechanism.** Environment variables read through `@Value` or `@ConfigurationProperties`, with no
  Spring Cloud Config, no Vault, no cloud secret manager — hosting infrastructure is out of scope, so the
  mechanism must be the one that works without any of them.
- **Fail-fast policy.** Which secrets are mandatory in which profiles, and whether a missing secret is a
  startup failure or a degraded mode. Startup failure is almost always right; name the exceptions.
- **Placeholder discipline.** IM8 **as-8** treats a committed placeholder as a finding when it ships to
  production. Decide what placeholders are permitted in the local profile and how they are prevented from
  travelling.
- **Local-dev ergonomics.** Whoever clones this repo must be able to run it. Decide what makes that true
  without any real secret being committed — a `.env.example`, documented defaults confined to the local
  profile, or generated-on-first-run values.
- **The handover document.** The map already carries HTTPS and HSTS handover notes as unspecified fog.
  Decide whether this ticket's output is that document or a section of it: the thing an operator reads
  before deploying, listing every secret, its format, its rotation obligation, and what breaks if it is
  wrong.

## Done when

Every secret in the inventory has a named source, a stated absence behaviour, and a rotation obligation
where one exists, and the handover document has an owner and an outline.

## Inherited from ticket 11 — a third key, and it is the one that cannot rotate

[Decide the admin module, role model, and initial admin bootstrap](11-admin-module-role-model-and-bootstrap.md)
adds one secret to the inventory and settles two items this ticket had listed as open.

**New: the tombstone HMAC key.** Deleted users are retained as tombstones, and the tombstone's email is stored
as a keyed HMAC rather than plaintext, so that reuse-blocking survives while an indefinitely-retained readable
address does not. Consequences this ticket must carry:

- **It cannot rotate, at all.** Rotate it and every existing tombstone stops matching, so reuse-blocking
  silently stops working on exactly the accounts it was protecting. This is the **second** entry in the
  category the ticket already opened with the password pepper — and unlike the pepper, which was declined and
  therefore never exists, this key exists. So the rotation policy's scoping sentence now has to name a key
  that is present in the running system and permanently frozen, not merely a hypothetical.
- **It must not share the TOTP secret encryption key**, which rotates yearly by design. One key with two
  rotation obligations, one of them impossible, resolves to the impossible one. Separate property, separate
  material.
- **Absence must be fail-fast**, and the reason is sharper than for the other secrets. A missing datasource
  credential fails loudly on first use; a missing TOTP key fails when someone enrols. A missing tombstone HMAC
  key means reuse-blocking **fails open silently** — registration starts accepting deleted users' addresses
  and nothing looks wrong. That is a security control degrading invisibly, so startup refusal is not merely
  the default answer here, it is the only safe one.
- Canonicalisation is fixed by ticket 11 (NFC, trim, lowercase) and the key is applied to the canonical form.
  If the key's encoding or length validation differs from the TOTP key's, say so explicitly rather than
  letting one property's rules be assumed for the other.

**Settled by ticket 11, no longer open here:**

- *Admin bootstrap credentials.* Environment variables only (`APP_ADMIN_USERNAME` / `APP_ADMIN_PASSWORD`),
  absent from every committed file including the dev profile, no default and no generate-and-log fallback.
  The seed is **not** profile-gated — Story 12 makes it a production necessity — so what is gated is the
  secret, not the mechanism. The seeded admin is flagged for mandatory password change.
- *Absence behaviour for those credentials.* Refusal to start, and specifically **during context refresh**
  via `@Validated @ConfigurationProperties`, not in the `ApplicationRunner` that performs the seeding. Boot
  starts the web server during refresh and runs runners afterwards, so a runner-based check means "bind the
  port, accept traffic, then die". Worth generalising into this ticket's fail-fast policy: every mandatory
  secret must be validated in a refresh-phase bean, because anything later has already accepted a request.

Ticket 11's validation also runs the seed password through the same `PasswordService` seam as user passwords,
so the 15-character floor and the zxcvbn gate apply to it — the bootstrap path is not a policy exemption, and
this ticket should not introduce a weaker rule for it.
---

## Answer

### 0. Corrections to this ticket's own framing

Four of the six inventory items the ticket opened with were already settled elsewhere and are carried
here as rows rather than decisions:

- **Session cookie name per profile** — fixed by [05](05-spring-security-7-config-surface.md) and
  [20](20-deployment-origin-topology.md) (`SESSION` in dev, `__Host-SESSION` outside it).
- **Admin bootstrap credentials** — fixed by [11](11-admin-module-role-model-and-bootstrap.md).
- **The conditional session `hash` HMAC key** — **does not exist**. Ticket 08 introduced no such key;
  09's `session.hash` log field rides the same facility as the email HMAC. Recorded so nobody goes
  looking for a property that was never created. *Consolidated into the register (ticket 33): R-CFG-010. Amend the table by ID, not this list.*
- **The TOTP key's property name** — already `app.mfa.totp.encryption.key` per ticket 23 §10. What was
  open was encoding, length, absence behaviour and validation placement, not naming.

The ticket's fifth bullet — "decide whether this ticket's output is that document or a section of it" —
is **stale**. It was written before [25](25-operational-handover-document.md) existed. Ticket 25 owns
the document and is blocked by this one, so the boundary is **policy here, procedure there** (§8).

### 1. The corpus has no secrets standard, and its one pattern is a fail-open

There is no secrets-management or configuration-handling standard anywhere in `App-Standards/`. What
exists is an MCC-family pattern using `${ENV_VAR:dev-default}` plus a per-profile `.env.sit`, and
`MCC_Shared_Auth_Recipes.md:1663` states plainly that without a `.env.sit` the SIT profile falls back to
dev-mcc defaults and works. That is a deployed environment silently running on development credentials,
and it is **rejected outright** (§3). *Consolidated into the register (ticket 33): R-CFG-006. Amend the table by ID, not this list.*

**Eighth standards defect on this map.** The normative contract those standards cite for how secrets
reach the application — `Appfw-Project-Bootstrap/Mcc/Mcc_Project_Bootstrap_Application_Standard.md#36-profile-configuration-contract`
— **does not exist in this repository**, and neither does `ProfileDotenvPostProcessor`, the loader it
names as the mechanism. Four standards cross-reference it (`MCC_Shared_Auth_Standard.md:444`,
`MCC_MFA_Application_Standard.md:313`, `MPDS_Retrieval_Standard.md:558`, `MPDS_Retrieval_Recipes.md:898`).
So the corpus's only statement of how secrets are supplied is a dangling reference. *Consolidated into the register (ticket 33): R-STD-056. Amend the table by ID, not this list.*

**IM8 `as-8`'s text names a specific annotation** — "`@Value("${...}")` from env; `.env` gitignored". We
deviate on the annotation and satisfy the intent. See §2 for the cost. *Consolidated into the register (ticket 33): R-CFG-007. Amend the table by ID, not this list.*

### 2. Binding mechanism: `@ConfigurationProperties`, and where validation may live

**Every secret binds through `@Validated @ConfigurationProperties`. No `@Value` on any secret path.**
Tickets 09, 11 and 23 already established refresh-phase validation as the pattern, and ticket 11's
reason is load-bearing: Boot starts the web server during refresh, so anything validated later has
already bound the port and accepted a request.

**The deviation's authority is Boot's own documentation, not our internal consistency.** Boot states
that `@Value` "is a core container feature, and it does not provide the same features as type-safe
configuration properties", tabulates its relaxed binding as **Limited** against `@ConfigurationProperties`'
**Yes**, and concludes: "If you define a set of configuration keys for your own components, we recommend
you group them in a POJO annotated with `@ConfigurationProperties`." So the ADR does not read "we departed *Consolidated into the ADR routing (ticket 34): ADR-062. Amend by ID, not this list.*
from `as-8`'s named annotation for internal consistency" — it reads **"`as-8` names the mechanism the
framework's own documentation recommends against for this purpose."** That is a materially stronger
position in front of a reviewer and it costs one citation. *Consolidated into the register (ticket 33): R-CFG-007. Amend the table by ID, not this list.*

**Cost, recorded honestly:** `im8-review` is a grep-shaped code audit whose `as-8` PASS condition names
`@Value("${...}")`. Our config may read as absent to it. This is the same class of false negative
ticket 01 already recorded for that tool's Boot 3.4 config spellings. *Consolidated into the register (ticket 33): R-CFG-007. Amend the table by ID, not this list.*

**Value-dependent validation must not be expressed as Bean Validation.** Boot's startup failure report
for a `@Validated @ConfigurationProperties` violation prints `Property:`, **`Value:`** and `Origin:`.
A wrong-length key would therefore be echoed in full to stdout by the very constraint that exists to
catch it, together with the file and line it came from. A validating *constructor* does not escape this
either: if the type is bound as a property, the throw surfaces as `ConfigurationPropertiesBindException`
and the report carries the same lines.

So the split is:

- **Bound as `String`, annotated for presence only** (`@NotBlank`). A missing key is `null`, so nothing
  is echoed.
- **Everything value-dependent runs in the `@Bean` factory method** that constructs the encryptor or
  MAC: Base64 decode with a strict decoder, length check, and the §7 content checks. Failures throw our
  own exception carrying **only the observed length**, never the value.

**`SecretKeyMaterial` therefore holds redaction and copying, not validation** — this is a deliberate
move of responsibility away from where the earlier draft put it. It redacts `toString()`, hands out
`byte[]` by defensive copy, and gives key material a type distinct from `String`. It is constructed by
the factory *after* validation has passed, so it never participates in property binding and can never
appear in a Boot failure report.

Actuator is a non-issue by default and a prohibited-configuration entry by design: since Boot 3,
`/env` and `/configprops` mask **all** values (`show-values` defaults to `never`), not merely
name-matched ones. Loosening that is prohibited (§9). The `key` substring in each property name is the
fallback if someone does.

### 3. Fail-fast: a structural rule, not a policy to remember

Every secret is **mandatory in every profile**. No defaults, no degraded mode, no exceptions. The
`${VAR:default}` idiom is prohibited on any secret. *Consolidated into the register (ticket 33): R-CFG-006. Amend the table by ID, not this list.*

Stated as a policy this is a rule someone has to remember. The structural form:

> **No secret's property name may appear in any committed properties or YAML file**, in
> `src/main/resources` or `src/test/resources`. *Consolidated into the register (ticket 33): R-CFG-006. Amend the table by ID, not this list.*

If the name is not there, there is nowhere to write a default. It is a deterministic unit test with no
external dependency, and it satisfies `as-8`'s "absent from every profile" literally rather than by
argument. Extended by §6 to cover `.env.example` for the admin password specifically. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-CFG-020. Amend the table by ID, not this list.*

**"The property name" means four spellings, not one.** Boot's relaxed-binding tables state that in
properties *and* YAML files, **camel case, kebab case and underscore notation all bind**. So for a
property `app.security.hmac.tombstone.key` a committed file may legally write `tombstone.key`,
`tombstone.Key`, or `tombstone_key` at the leaf, and an assertion matching one literal passes on the
others. The rule is therefore **one normalising regex per secret** — match the segment sequence with
dots, dashes, underscores or camel humps permitted between segments — not an enumeration of literals,
which is both cheaper and impossible to get partially right.

**And the environment-variable spelling is not the obvious one.** Boot documents the canonical-form
conversion as: replace dots with underscores, **remove any dashes**, uppercase — with the worked example
`spring.main.log-startup-info` → `SPRING_MAIN_LOGSTARTUPINFO`. So a dashed leaf segment produces a
run-together environment variable that no operator would guess, the docs do not state whether the natural
underscore form also resolves, and the failure mode is either refusal to start or — if someone sets both
spellings — §4's override chain silently picking one. **This is why §8.1's three new properties are
dot-separated at every segment** (see §8.1). `app.admin.password` was already safe for the same reason:
no dashes anywhere in it.

Tests supply key material through `@DynamicPropertySource` in Java, never through a committed test
properties file, so the assertion holds for test resources too.

What the rule cannot catch is a value that is present but wrong. That is §7's job.

### 4. Delivery: file is the source of record, environment variables override it

Two import locations, both native, no dotenv dependency:

```properties
spring.config.import=optional:file:./.env[.properties],optional:configtree:/run/secrets/
```

The mounted path uses **`configtree:`, not a properties file**. Boot documents `configtree:` for exactly
this case — one file per secret, filename becoming the property name, contents becoming the value — and
names both Kubernetes Secret volume mounts and Docker secrets, with `optional:configtree:/run/secrets/`
as its own worked example. A single `app.properties` would have assumed a shape the platform does not
produce: a mounted Kubernetes Secret is a **directory of files**, not one properties file. Filenames with
dot notation map directly, so `/run/secrets/app.security.hmac.tombstone.key` needs no directory nesting.

This also removes §6's entire problem class from the deployed path. There is no properties parsing, so no
backslash escaping, no `#`/`!` comment characters, no stripped leading whitespace and no ISO-8859-1
question — the value is the file's content. See §6 for what that changes.

Boot also documents that config-tree values bind to **`String` or `byte[]`**, which means raw binary key
material could be mounted and bound directly with no Base64 at all. **Declined for the three keys**, so
that §7's single encoding rule covers both delivery paths; two encodings for one key is worse than one.
**Taken for the admin password**, for the reason in §6.

Industry practice supports the file preference — Red Hat documents volume mounts as the recommended way
to consume secrets on OpenShift and Kubernetes, because environment variables leak through
`/proc/<pid>/environ`, child processes, crash dumps and container inspection. It also narrows what we must
write in the 13.3.1 register entry from "environment variables only" to "file-mounted secrets, no vault". *Consolidated into the register (ticket 33): R-CFG-013. Amend the table by ID, not this list.*

**The precedence is the opposite of the intuition, and it is an operational trap.** Boot's documented
property-source order lists config data at position 3 and OS environment variables at position 5, with
later sources overriding earlier ones. Imported files are config data. So:

```
application.properties  <  first import  <  second import  <  OS environment variables
```

A leftover `APP_MFA_TOTP_ENCRYPTION_KEY` in a shell profile, systemd unit or container spec **silently
wins over the mounted file you just rotated**. Nothing fails. The application starts on the old key. For
the TOTP key that surfaces as decryption failures after a rotation that appeared to succeed; for the
tombstone key it surfaces as nothing at all. **Ticket 25 must carry the sentence "the file is the source
of record and environment variables override it"**, because an operator who believes the file wins will
debug in the wrong place.

This is the second reason the §7 fingerprint is not a nicety: it is the only detection for a shadowed
key, as well as for a present-but-wrong one.

Boot's docs do settle two things we would otherwise have had to test: an imported file's values take
precedence over the file that declared the import, and an import is processed once however many times it
is declared. What they do **not** settle is the relative order of **two imports declared in a single
document** — "inserted immediately below the declaring document" does not disambiguate siblings, and the
last-wins rule is documented for `spring.config.location`, a different property. Since rotation
correctness now depends on whether `/run/secrets` beats `.env`, **that specific fact gets a one-line
assertion**, not the chain as a whole. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-CFG-021. Amend the table by ID, not this list.*

**The import path is hard-coded.** Not for tidiness: a property that says where secrets come from cannot
itself come from secrets, so making it configurable creates a bootstrap ordering problem for no gain.

### 5. Local development, and why generate-on-first-run is refused

`.env` at the repo root, gitignored, parsed by Boot as properties. `.env.example` committed with
angle-bracket placeholders, following the corpus's own `.env.sit.example` convention.

**Generate-on-first-run is rejected.** For the tombstone key a regenerated key is indistinguishable from
the correct one at every layer except the fingerprint, and it silently stops blocking reuse — turning
the one failure mode we deliberately made fail-fast back into the silent fail-open it was built to
prevent. The honest reason to refuse it is not invisibility, since §7's fingerprint makes it detectable:
it is that **nobody backs up a key they never knew was minted**.

### 6. `APP_ADMIN_PASSWORD` never travels through the file

`.env` is parsed with `java.util.Properties` semantics, which are not shell-dotenv semantics: backslash
escapes and continues lines, `#` and `!` start comments, leading whitespace is stripped, and `:` also
separates key from value. Base64 key material is unaffected — the alphabet contains no backslash, and
only the first unescaped separator counts, so trailing `=` padding survives. URLs are unaffected for the
same reason.

`APP_ADMIN_PASSWORD` is the exception: it is a human-chosen passphrase of 15+ characters that may
contain any of those characters, and a mangled bootstrap password fails at first login with no
diagnostic.

**So the rule is a property of the file, not a caveat about one key: `.env` holds no human-chosen text.**
The bootstrap credential never travels through `.env`, and the §3 name-absence assertion is **extended to
`.env.example`** for that property — enforced on the artefact we control rather than trusted to a comment
somebody reads. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-CFG-020. Amend the table by ID, not this list.*

**But it is not forced onto an environment variable.** §4's `configtree:` mount does no properties parsing
at all, so the deployed path can carry the passphrase safely — which matters, because §4's whole reason for
preferring files is that environment variables leak through `/proc`, child processes and crash dumps. An
earlier draft of this section pushed the one credential a human types onto the leakiest channel. The
resolution: **`/run/secrets/app.admin.password` is the preferred deployed route**, with the environment
variable retained as a fallback.

One unknown, handled rather than assumed: **Boot does not state the charset used to read config-tree
values.** So the password binds as **`byte[]`** — which Boot documents config-tree values support — and we
decode UTF-8 ourselves. That removes the question instead of resting on an unverified answer, and it costs
nothing, because ticket 07's pipeline already has to NFC-normalise and count UTF-8 bytes on that value.

Corroborating but **not** the primary reason: Boot imports properties files as **ISO-8859-1** by default,
so a non-ASCII passphrase is silently corrupted — and ticket 07 mandates NFC normalisation and counts the
72-byte ceiling in UTF-8 bytes, which presumes non-ASCII passphrases are permitted. That argument is
repairable with the documented `[encoding=utf-8]` attribute, whereas nothing repairs backslash-as-escape
or `#`/`!` as comment starters. Keep the rule on the escaping ground. Worth recording that for our
specific file shape the repair may not even be expressible: it would need `[encoding=utf-8]` **and** the
`[.properties]` extension hint on the same extensionless file, and the docs give no example of combining
two bracket hints.

### 7. Rules common to all key material

Stated once rather than per row, because they are identical for all three keys.

| Rule | Value |
| --- | --- |
| Encoding | Standard Base64, padded. Strict decoder; a decode failure is the same class of error as absence. |
| Length | **Exactly 32 decoded bytes** = 44 Base64 characters. |
| Why 32 | Not a preference. ASVS Appendix C grades **AES-128 as Legacy (L)** and AES-256 as Approved, so a 16-byte key puts us on the Legacy list. For HMAC-SHA-256, a key of at least the hash output length is what gives full strength. |
| Where validated | The `@Bean` factory, never Bean Validation. §2. |
| Failure message | Observed length only. Never the value, never the origin. |
| Fingerprint | One INFO line per key at startup: property name, key version where one exists, and **8 hex characters** of a **domain-separated digest over the decoded bytes**. |

**Why the fingerprint is computed that way.** Over *decoded* bytes, because the same key with different
padding or a trailing newline would otherwise produce a different fingerprint and an operator comparing
two environments would conclude the keys differ when they do not. **Domain-separated** — a fixed label
folded in rather than a bare digest of the key material — because at 256 bits a bare digest is not
practically attackable, so this is not a defect; it is one string constant that forecloses the argument
a reviewer will actually make. The corpus has precedent: MCC Recipe 8 logs the selected key source and
`kid` at INFO while prohibiting key material at every level.

**Generation.** Nothing else in this ticket guarantees the key is *random*. §3 guarantees presence,
§7 guarantees length and encoding, and neither touches entropy. ASVS **11.5.1 (L2)** is the hook — CSPRNG,
at least 128 bits. (11.6.1 is **not** a valid citation here: it sits in V11.6 Public Key Cryptography and
its worked example is RSA keys vulnerable to Fermat factorization.)

The documented command, with a PowerShell form because this repo is developed on Windows:

```bash
openssl rand -base64 32
```

```powershell
$b = [byte[]]::new(32)
[System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b)
[Convert]::ToBase64String($b)
```

`Get-Random` **must not** be used. It is backed by a non-cryptographic generator and it is the obvious
thing a Windows operator reaches for.

Enforced alongside it, one check: **reject a key whose decoded bytes are all printable ASCII**, and reject
all-identical bytes. The realistic operator error is not a weak random key, it is Base64-encoding a
*string* — `echo "my-super-secret-key" | base64`, or encoding a password manager's alphanumeric output.
Both produce a length-valid 32-byte key whose plaintext is entirely printable, and for 32 bytes of CSPRNG
output that occurs with probability around 10⁻¹⁴. One loop, no realistic false positive. *Consolidated into the register (ticket 33): R-CFG-008. Amend the table by ID, not this list.*

A known-bad test-vector check is **deliberately not added**: the set is not enumerable, so it could only
ever cover the vectors someone thought of, which is precisely the kind of control-that-does-not-hold this
ticket is trying to keep out of the register. Printable-ASCII plus all-identical is finite and honest.

So **11.5.1 is recorded satisfied-by-procedure, not satisfied-by-enforcement**, with the printable-ASCII
check named as what is actually enforced. A startup check cannot distinguish 32 CSPRNG bytes from 32
bytes of a password manager's output, and claiming otherwise would put a fourth unenforceable control in
the register. *Consolidated into the register (ticket 33): R-CFG-008. Amend the table by ID, not this list.*

### 8. The secrets inventory

#### 8.1 Key material and credentials

| Property | Purpose | Source | Absence | Rotation | Profiles | ASVS |
| --- | --- | --- | --- | --- | --- | --- |
| `app.mfa.totp.encryption.key` | AES-256-GCM key wrapping the TOTP seed at rest | mounted file (preferred) or env; **absent from every committed file** | refuse to start, refresh phase | **Yearly**, incremental via key-version column (ticket 19/23) | all | 13.3.1 **F**, 13.3.4 partial, 11.3.2 **S** |
| `app.security.hmac.tombstone.key` | HMAC-SHA-256 over the canonicalised email of a deleted user | as above | refuse to start, refresh phase | **Forward only** — new versions mint, old versions never retire | all | 13.3.1 **F**, 11.2.2 partial |
| `app.security.hmac.log.key` | HMAC-SHA-256 for `source.ip.hash` and `session.hash` log fields | as above | refuse to start, refresh phase | **Freely** — correlation need not span a rotation | all | 13.3.1 **F**, 13.3.4 **S** for this row |
| `app.admin.password` (`APP_ADMIN_PASSWORD`) | Bootstrap admin credential (ticket 11) | `/run/secrets/` preferred, env fallback; **never `.env`** (§6). Bound as `byte[]`, UTF-8 decoded by us | refuse to start, refresh phase, validated through `PasswordService` | Mandatory change at first login; no periodic expiry (NIST forbids) | all | 13.3.1 **F** |
| `app.admin.username` (`APP_ADMIN_USERNAME`) | Bootstrap admin identity — credential-adjacent, not key material | as above | refuse to start, refresh phase | n/a | all | — |

**Rows are named by property, with the environment spelling in parentheses.** Ticket 11 specified the
bootstrap credential by its environment-variable spelling, which is what exposed §3's assertion hole: the
*property* is `app.admin.password`, the spelling ticket 01 used, and a committed `app.admin.password:` in
a YAML file passes an env-var-name check cleanly. Property name is the canonical form; everything else is
a projection of it.

**The three properties created by this ticket are dot-separated at every segment** —
`hmac.tombstone.key`, `hmac.log.key`, `hmac.tombstone.version` — rather than the kebab-case leaf Boot
generally recommends. The reason is §3's environment-variable mapping: a dashed leaf has its dash
*removed*, so `app.security.hmac.tombstone-key` would be `APP_SECURITY_HMAC_TOMBSTONEKEY`, which no
operator will guess and which the docs do not confirm also resolves from the natural underscore form.
Dot-separated leaves make the environment spelling obvious and take the dash-removal rule out of play
entirely. Recorded as a deliberate deviation from the kebab-case recommendation, with the trade named:
we accept a slightly deeper properties object to remove a class of operator error. *Consolidated into the register (ticket 33): R-CFG-009. Amend the table by ID, not this list.*

**Why the tombstone and log keys are separate.** Ticket 09 routed `source.ip.hash` through ticket 11's
tombstone HMAC facility and recorded "no new key and no new argument", with domain prefixes (`email:` /
`ip:`) letting the two streams "be reasoned about independently at rotation". **That is withdrawn.** One
key cannot rotate for the IP stream and stay frozen for tombstones; the prefix buys cross-correlation
resistance, not independent rotation. This ticket's own opening rule — one key with two rotation
obligations resolves to the impossible one — decided the identical question against sharing when the TOTP
key was the candidate, and applies here unchanged.

The authority is **NIST SP 800-57 Part 1 Rev 5 §5.2 Key Usage**, reached through ASVS **11.1.1 (L2)**,
which requires a key lifecycle following a key-management standard *such as SP 800-57*. It is **not**
11.1.1's "not overshared" parenthetical, which bounds how many *entities* hold a key — its own example is
more than two entities for a shared secret — and says nothing about one key serving two purposes. A
reviewer following that reference would find the mismatch.

Two further arguments the ticket had not made. The keys' **absence severities differ**: a missing
tombstone key fails open silently, so registration starts accepting deleted users' addresses with nothing
looking wrong, while a missing log key degrades a log field. Same fail-fast answer, different severity,
and one property cannot state both. And the log key is the **only key on this map whose rotation is both
possible and free of user impact**, so folding it into the frozen one throws away the only cheap rotation
available. Ticket 09's IPv4 residual — a 2³² space is brute-forceable if the key leaks — is answered by
rotation only if the key can rotate.

**The tombstone key rotates forward.** Ticket 11 recorded that it "cannot rotate, at all". That is
**corrected**: forward rotation works with the same key-version idiom tickets 19 and 23 built — a version
column, new tombstones written under the newest key, candidates verified against every retained version
at a cost of one HMAC per version per registration attempt. What is impossible is *retiring* a version,
because we do not hold the plaintext to re-derive it. So the honest statement is **"versions accumulate
and never retire"**, not "the key never rotates" — and that distinction is what turns ASVS 11.2.2 (L2)
from a clean failure into a partial, and gives a leaked key a forward response where previously there was
none. *Consolidated into the register (ticket 33): R-CFG-014. Amend the table by ID, not this list.*

Two things pinned with it: a **current-version property** mirroring `app.mfa.totp.encryption.key-version`,
so "newest key" is never implicit in map ordering or property naming; and **constant-time comparison per
candidate, with the matched version never reported**. ASVS 11.2.4 is L3 so the latter is informational,
but it is free at design time and not free later.

Cost to state plainly: a registration attempt's cost grows with rotation history and nothing ever shrinks
it. At yearly rotation that is negligible for this application's life, and saying so beats leaving a
reviewer to work out whether we noticed. *Consolidated into the register (ticket 33): R-CFG-014. Amend the table by ID, not this list.*

**Deterministic authenticated encryption was considered and declined.** AES-SIV or AES-GCM-SIV would give
the same equality-matching property with full re-keyability, satisfying 11.2.2 (L2) outright. It fails at
**L1**, which is our whole-application target: ASVS 11.3.2 (L1) requires approved ciphers and modes, and
Appendix C's approved AEAD list is AES-GCM, AES-CCM, ChaCha-Poly1305, AEGIS-128/128L/256 and
Encrypt-then-MAC — **no SIV of any kind**. NIST places AES-GCM-SIV on its *Proposed Modes* page, which
states that appearance there does not constitute endorsement or approval; AES-SIV is not even proposed.
There is also no JDK provider and no SIV mode in Spring Security's `Encryptors`, so it would mean
BouncyCastle or Tink — a new crypto dependency on the exact path ticket 19 kept inside Spring Security
after CVE-2026-47842. Trading an L1 requirement to satisfy an L2 one is the wrong direction. Ticket 11's
PDPA argument — a decryptable tombstone means we still hold recoverable personal data after deletion — is
the second reason, not the first.

**Secrets that deliberately do not exist**, recorded so nobody adds them or files their absence as a
finding: no password pepper (ticket 07 — a peppered hash cannot be rotated, and this ticket's rotation
policy scopes itself to keys that can); no keyed hash for credential tokens (ticket 10 — plain
domain-separated SHA-256 over a 256-bit token has nothing to brute-force); no session `hash` key; no
CSRF cookie and therefore no CSRF cookie secret (ticket 08 turned that clause into a negative assertion).
The H2 datasource credential is **not a secret** — see §8.2. *Consolidated into the register (ticket 33): R-CFG-010. Amend the table by ID, not this list.*

#### 8.2 Environment-varying configuration

| Property | Value / source | Absence | Profiles | Owner | ASVS / notes |
| --- | --- | --- | --- | --- | --- |
| `app.origins.spa` | SPA origin. Single source for the CORS allow-list **and** ticket 10's link origin | refuse to start | required in all; value differs | **24** | Supersedes `app.cors.allowed-origins` (ticket 05) |
| `app.origins.api` | API origin. Cross-checked against the derived CORS config at refresh | refuse to start | required in all; value differs | **24** | — |
| `app.mfa.totp.encryption.key-version` | Current TOTP key version, 1 byte, bound inside the ciphertext prefix | refuse to start | all | 23 | Makes yearly rotation incremental |
| `app.security.hmac.tombstone.version` | Current tombstone key version | refuse to start | all | **24** | New here; §8.1 |
| `spring.datasource.url` | dev: `jdbc:h2:file:./data/authdb;LOCK_TIMEOUT=1000` | **see §9** — absence is *not* fatal | dev only; non-dev has no committed value | **24** | 13.2.1 **F** |
| `spring.datasource.username` / `.password` | Non-default name, non-empty local value. **Declared non-secret** | n/a | dev | **24** | 13.2.3 **S**, 13.2.2 **F** |
| `app.security.client-ip.source` | `socket` (default) or `proxy` | defaults to `socket` | all | 09 | Insecure default made unreachable |
| `app.security.client-ip.trusted-proxies` | **No default.** Drives Tomcat `internal-proxies` *and* `forward-headers-strategy` from one input | refuse to start when `source=proxy` | all | 09 | Ticket 25 owes the why-not-shortcut text |
| `server.servlet.session.cookie.name` | `SESSION` dev / `__Host-SESSION` non-dev | — | per profile | 05 / 20 | Asserted by test, non-dev never executed |
| `server.servlet.session.cookie.secure` | `false` dev / `true` prod | — | per profile | 05 | As above |
| `spring.session.timeout` | `15m` — one property; setting `server.servlet.session.timeout` too is how you get a silent mismatch | — | all | 05 / 08 | — |
| `spring.session.jdbc.initialize-schema` | `never` — Flyway owns the DDL | — | all | 05 | Prohibited at `always` (§9) |
| `api.base-path` | `/api/v1` | — | all | 08 | — |
| `app.mfa.totp.*` | `issuer` required with no default; `period`/`digits`/`secret-bytes`/`skew-steps`/`qr-size`/`pending-ttl`/`lockout.*` per ticket 23 §10 | `issuer` blank ⇒ refuse to start | all | 23 | Prefix deviates from the recipe's `spring.eds.mfa.totp` |
| `app.security.password.*` | Ticket 07's block verbatim | — | all | 07 | 11.4.2 **S** (bcrypt ≥ 10 approved; ours is 12) |
| `app.security.roles`, `app.security.url-guards` | YAML is the source of truth; startup validator fails on YAML/table disagreement | refuse to start on mismatch | all | 11 | — |
| `VITE_CSP` | Document CSP, substituted into `index.html` via `%VITE_CSP%` | build fails loudly | per mode | 20 | **Build-time, non-secret, ships in the bundle by design** |
| `VITE_API_ORIGIN` | API origin for the SPA client **and** the `connect-src` directive | Build-time substitution; asserted post-build (§15) | per mode | 20 / **24** | As above; see the lifecycle mismatch note below *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-SES-011. Amend the table by ID, not this list.* |

**A drift the single-source rule does not reach.** `app.origins.api` is resolved at **runtime** on the
backend; `VITE_API_ORIGIN` is frozen at **build time** in the bundle. Section 8.2's single-source rule
keeps each side internally consistent — CORS and link generation both read `app.origins.spa`, and
`connect-src` and the API client both read `VITE_API_ORIGIN` — but the two sides now have *different
lifecycles*, so changing the backend origin without rebuilding the frontend leaves the pair disagreeing
with nothing to catch it. Worse, the symptom is a blocked API call, which presents as a CSP problem and
will be debugged as one. Ticket 07's Q7 named backend-to-frontend agreement as the seam only an
end-to-end test can check; this makes it sharper than "unchecked", and it is a required handover line
rather than a note. *Consolidated into the register (ticket 33): R-HDR-007. Amend the table by ID, not this list.*

Ticket 09's client-IP rows and the per-profile cookie rows are carried here even though other tickets
decided them, because this table is the operator's single read: a row omitted on the grounds that
somebody else owns it is a property the operator does not know exists.

### 9. Prohibited configuration

Five tickets each invented a bespoke startup check. Consolidated into **one refresh-phase validator**,
each entry carrying the reason it exists:

| Entry | Reason |
| --- | --- |
| `spring.mvc.servlet.path` set at all | CVE-2026-22753 silently disabled the whole filter chain in exactly that configuration, on exactly our endpoint (11, 23) |
| `server.forward-headers-strategy: framework` | `ForwardedHeaderFilter` validates nothing; a spoofable key nullifies the limiter rather than weakening it (09) |
| `server.tomcat.remoteip.internal-proxies` set directly | Tomcat's shipped default trusts every RFC 1918 peer (09) |
| `spring.session.jdbc.initialize-schema: always` | `EMBEDDED` default creates nothing on a real database and `continue-on-error: true` masks it (05) |
| `management.endpoint.env.show-values` / `configprops.show-values` ≠ `never` | Boot 3+ masks all values by default; loosening it exposes bound key material. 13.4.5 (L2) |
| Any clustering-related property | The rate limiter is in-memory and single-instance (09) |
| Any secret property name in any committed properties/YAML | §3 |
| Resolved JDBC URL is **H2 in-memory**, or is not H2-file when the dev profile is active | §9 below, and the ticket 20 collision |
| `server.servlet.session.cookie.max-age` | Converts a browser-session cookie into a persistent one — remember-me by another name (08) |

This names a concept the map was missing: **prohibited configuration**, distinct from *invalid*
configuration — a value that binds correctly and is well-formed but that we have argued must never be
set. Glossary entry owed, because the reason behind each entry is a CVE or an outage, and a validator
without reasons attached gets pruned by whoever finds it noisy.

**The validator is a single point of deletion**, which is the risk shape ticket 09 already recorded for
the rate limiter's exception branch. So: a test asserting the validator bean is present, **and** one
assertion per entry that the prohibited value actually trips it. Otherwise the consolidation trades nine
things to forget for one thing to delete. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-CFG-024, T-CFG-025, T-CFG-026, T-CFG-027, T-CFG-028, T-CFG-029, T-CFG-030, T-CFG-031, T-CFG-032, T-CFG-033. Amend the table by ID, not this list.*

**Implementation constraint.** If the validator is realised as a bean named
`configurationPropertiesValidator`, Boot requires its `@Bean` method be declared **`static`** — the
validator is created very early, before the enclosing `@Configuration` class can be instantiated, and a
non-static declaration causes early-instantiation problems rather than a clean failure. Pinned here
because it is exactly the kind of thing that produces a confusing startup error in `/do-work`.

**The datasource entry collided with ticket 20's test strategy, and the entry is what changed.** The
earlier draft refused startup whenever the resolved JDBC URL was H2 outside the dev profile. But H2 is the
only database on the classpath and no other datasource is configured anywhere, so that entry refuses
startup under *any* non-dev profile — and ticket 20 settled that production is a documented requirement
asserted by tests that never execute, including a `@Profile("prod")` cookie-posture assertion. Any such
test that refreshes a real application context would fail on the datasource instead of asserting the
cookie, so the §9 test and ticket 20's assertions could not both pass.

Resolved two ways, and both are needed. The entry is **narrowed** to "must not be H2 in-memory" plus
"must be H2-file when dev is active", dropping the non-H2-outside-dev half — which keeps the finding that
matters, since the ephemeral-in-memory fallback was the actual hazard, not H2 itself. And ticket 20's
prod-only values are asserted **without refreshing a context**, via `ApplicationContextRunner` or direct
property-binding assertions. The second is the more important half: it is the only form consistent with
ticket 20's own claim that the profile is *never executed*, and a test that refreshes a prod context is
executing it. *Consolidated into the register (ticket 33): R-CFG-004. Amend the table by ID, not this list.*

**Why the datasource row needs the validator rather than absence.** If `spring.datasource.url` is unset
and H2 is on the runtime classpath — which it must be, since H2 is our only database — Boot **does not
fail**. `DataSourceProperties` detects an embedded connection and resolves
`jdbc:h2:mem:<uuid>;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false`. A non-dev deployment with no datasource
configuration starts cleanly on an **ephemeral in-memory database**: users, sessions, tombstones and TOTP
secrets all vanish on restart. Worse, **Flyway then migrates that database successfully and
`ddl-auto: validate` passes**, so all three layers agree nothing is wrong. It also silently drops ticket
09's `LOCK_TIMEOUT=1000`, which was pinned on the URL precisely so it would be visible where the
datasource is configured — so the pessimistic-lock behaviour three tickets depend on changes with no
configuration having changed. Absence must be converted into a failure because Boot will not do it.

### 10. The datasource: three L2 failures, one of them cheaply closed

`sa` with an empty password is a **default credential** (13.2.3), an **unchanging password credential**
(13.2.1), and a **superuser** (13.2.2).

Separating a migration identity from a restricted runtime identity was considered and **declined**.
Three facts kill it:

1. **H2's `GRANT` applies to tables, not schemas.** Current H2 2.x docs describe it as granting rights
   "for a table"; the `ON SCHEMA schemaName` form appears in 1.4.x-era grammar references and could not
   be confirmed in the 2.x documentation. Table scope means every future migration must remember a
   matching `GRANT`, and a forgotten one fails at first use rather than at migration time.
2. **[h2database#2846](https://github.com/h2database/h2database/issues/2846) — "GRANT SELECT, INSERT,
   UPDATE, DELETE incorrectly gives privileges to drop a table"**, with a maintainer noting in the same
   thread that H2 only allows admin users to own a schema. So a DML-only grant may confer DROP. This
   kills the *claim*, not just the convenience: asserting 13.2.2 on an engine with that open defect is
   the same failure shape as ticket 11's inert `ImmutableSecurityHandler` — a guard that looks like a
   control and is not. Recording the failure is better than shipping the appearance of one.
3. **Renaming `sa` does not help by itself.** In H2 the user named on the first connection to a new
   database *becomes* its administrator, so `spring.datasource.username=app` produces an admin called
   `app`.

What is kept: **a non-default username and a non-empty local password**, which genuinely satisfies
13.2.3 — its text is specifically about default credentials such as root/root. 13.2.1 and 13.2.2 are
recorded as failed, scoped to a dev-only embedded database that ships no production datasource. *Consolidated into the register (ticket 33): R-DATA-015, R-DATA-016. Amend the table by ID, not this list.*

**The H2 credential is declared non-secret**, explicitly, and is therefore absent from §8.1 and outside
§3's assertion. It guards a file that whoever holds it can already read, so a committed dev value confers
nothing. This is the same move §11 makes for the frontend env files. Note that H2 `CIPHER=AES` would
break this reasoning — under file encryption the password becomes real key material — which is a third
independent reason it stayed declined, alongside adding a fourth mandatory secret and the fact that its
file password must be supplied as `"filePassword userPassword"` space-separated inside the password
field, colliding directly with §6. *Consolidated into the register (ticket 33): R-CFG-011. Amend the table by ID, not this list.*

**One claim withdrawn.** An earlier draft said local file access yields no secrets because the TOTP
ciphertext is useless without the env-supplied key. The first half stands; the sentence does not. The
same file holds `SPRING_SESSION`, whose `SESSION_ID` column is the token the cookie carries — Spring
Session's `DefaultCookieSerializer` has Base64-encoded it since 2.0, which is an encoding, not
protection. **File read means live session hijack**, not merely offline BCrypt work. Scoped, not
mitigated: this is a developer-workstation risk on a database that never reaches a deployment. *Consolidated into the register (ticket 33): R-SES-005. Amend the table by ID, not this list.*

### 11. Placeholder discipline, and the committed frontend env files

Ticket 20 commits `.env.development` and `.env.production`. `as-8` says `.env` gitignored. A reviewer
grepping for committed `.env` files finds two and reaches for `as-8` immediately, and "those ones are
fine" is not a control.

- **`.env.example`** committed with **angle-bracket placeholders**. Four reasons over a real-looking
  Base64 value, the fourth being the strongest because it is internal: a 44-character Base64 string in a
  committed file is exactly what §12's scanner fires on, creating a permanent false positive and an
  allowlist entry — the decay path §12 exists to avoid. Angle brackets also fail §7's length validation,
  so they are already caught.
- **Frontend env files stay committed**, with a header comment in each declaring them build-time
  non-secret configuration. **`VITE_*` values are not confidential — they ship in the bundle by design**,
  stated plainly so nobody later treats them as secret material and reopens this.
- **Two assertions, not a regex.** An **allowlist** test that the set of keys in `.env.production` is
  exactly `{VITE_CSP, VITE_API_ORIGIN}` — a secret-shaped-value heuristic would be a guess, while an
  allowlist fails the moment anyone adds a key at all, which is the event we care about. Plus the
  complementary assertion that **the committed API origin is still a placeholder**, because the real risk
  is not a missing key but somebody pasting a real hostname in during a deployment push. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-CFG-034, T-CFG-035. Amend the table by ID, not this list.* *Consolidated into the register (ticket 33): R-CFG-012. Amend the table by ID, not this list.*

### 12. Secret scanning

The §3 absence assertion **stays**. It and a scanner are different controls: the assertion is a
deterministic unit test with no external dependency, no ignore file and no tuning, asserting one
invariant. A scanner is an external tool with a known decay path. Replacing the first with the second
would trade a control that cannot rot for one that can, to remove five lines of test code.

**Two scanner invocations, not one.** An earlier draft wrote "scoped to git history and placed at
pre-commit", which conflates two different commands with different cadences:

- **A history scan** over past commits — run once now, re-run occasionally. This is the one that matters
  for 13.3.1, because a working-tree scan goes green while a deleted-but-committed secret remains in
  history, and anyone with the clone still has it.
- **A pre-commit hook** over staged content — continuous, and the only mode that *prevents* rather than
  reports. *Consolidated into the register (ticket 33): R-BLD-011. Amend the table by ID, not this list.*

Tuned narrow, which is affordable precisely because it is not doing the assertion's job: the three secret
property names plus generic key-shaped patterns.

**Two limitations stated rather than left to be found.** With no CI in scope this runs only when someone
runs it locally, exactly like OWASP Dependency-Check. And pre-commit hooks are **local, not shared, and
bypassable with `--no-verify`** — so the hook is a convenience for the person who installed it, not a
control the repository enforces. The §3 assertion is the part that cannot be bypassed, which is the
second reason it stays. *Consolidated into the register (ticket 33): R-BLD-011. Amend the table by ID, not this list.*

### 13. The ASVS register

Recorded with levels, per the map's rule. The map's target is **ASVS 5.0 L1** with named L2/L3 controls
adopted where cheap.

**Failed, knowingly — one deferral, two requirement IDs.** ASVS **13.3.1 (L2)** and **13.3.3 (L3)**.
Cause: hosting infrastructure is out of PRD scope, so there is no vault, no cloud KMS and no HSM.
Sharper than it first appears — 13.3.1's enumeration of what belongs in the vault explicitly includes
*keys and seeds for time-based tokens*, so **the TOTP seeds themselves** are in scope, not only the key
wrapping them. Written as one deferral with two IDs rather than two findings, because there was one
decision. Compensating controls: file-mounted or env-supplied keys never in the database and never in
VCS, refusal to start on absence, secret names absent from every committed config, and §12's history
scan. *Consolidated into the register (ticket 33): R-CFG-013. Amend the table by ID, not this list.*

**Partial.** **11.2.2 (L2)** — algorithms, modes and key lengths are swappable, and the TOTP key rotates;
the tombstone key's old versions can never retire. **13.3.4 (L3)** — the TOTP key rotates yearly and the
log key freely; the tombstone key only forward. **11.5.1 (L2)** — satisfied by procedure, enforced only *Consolidated into the register (ticket 33): R-CFG-014. Amend the table by ID, not this list.*
to the extent of §7's printable-ASCII and all-identical checks. *Consolidated into the register (ticket 33): R-CFG-008. Amend the table by ID, not this list.*

**Failed, datasource.** **13.2.1 (L2)** and **13.2.2 (L2)**, per §10. *Consolidated into the register (ticket 33): R-DATA-015. Amend the table by ID, not this list.*

**Satisfied.** **13.1.4 (L3)** — *conditional on ticket 25 being written*, recorded that way so the ticket
18 gate does not read a forward reference as a completed control, which is precisely the collapse ticket
25's own preamble warns about. **11.1.1 (L2)** — documented key-management policy following SP 800-57. *Consolidated into the register (ticket 33): R-CFG-015. Amend the table by ID, not this list.*
**11.1.2 (L2)** — satisfied only with §13.1's algorithm inventory added; see below. **11.3.2 (L1)**, *Consolidated into the register (ticket 33): R-CFG-016. Amend the table by ID, not this list.*
**11.2.3 (L2)**, **11.4.1 (L1)** — AES-256-GCM, HMAC-SHA-256, SHA-256, all Approved in Appendix C, all
≥128 bits of security. **13.2.3 (L2)** — §10. **13.4.5 (L2)** — actuator exposure allowlist, *Consolidated into the register (ticket 33): R-CFG-017, R-DATA-016. Amend the table by ID, not this list.*
`show-values` prohibited above `never`, authenticated base path. *Consolidated into the register (ticket 33): R-OBS-011. Amend the table by ID, not this list.*

**11.1.2 was overclaimed on a keys-only table.** Its text requires the inventory to include all
cryptographic **keys, algorithms and certificates**, and §8.1 is keys. Rather than downgrade to partial,
the algorithm inventory is added, because every value in it is already decided elsewhere: *Consolidated into the register (ticket 33): R-CFG-016. Amend the table by ID, not this list.*

#### 13.1 Algorithm inventory

| Use | Algorithm | Parameters | Decided in |
| --- | --- | --- | --- |
| TOTP seed at rest | AES-256-GCM | 16-byte random IV, 16-byte tag, 69-byte envelope, context prefix in plaintext | 19, 23 |
| TOTP computation | HMAC-SHA-1 | RFC 6238, 30 s period, 6 digits, ±1 window | 22, 23 |
| Tombstoned email | HMAC-SHA-256 | domain-prefixed, over the NFC/trim/lowercase canonical form | 11, 24 |
| Log field hashes | HMAC-SHA-256 | domain-prefixed (`ip:` / `session:`) | 09, 24 |
| Password storage | BCrypt | cost 12 behind `DelegatingPasswordEncoder`; Appendix C requires ≥ 10 | 07 |
| Credential tokens | SHA-256 | plain, domain-separated `SHA-256(type ‖ ":" ‖ token)`; no HMAC | 10 |
| Token generation | `SecureRandom` | 256-bit, Base64url unpadded | 10 |
| Key generation | CSPRNG | §7; 11.5.1 (L2) satisfied-by-procedure | **24** |
| Key fingerprint | SHA-256 | domain-separated, over decoded bytes, truncated to 8 hex | **24** |

No certificates: TLS termination is nobody's in this scope (ticket 20), and there is no client-certificate
authentication and no signing key anywhere in the application. *Consolidated into the register (ticket 33): R-CFG-016. Amend the table by ID, not this list.*

**Unowned and recorded here because this is the ticket that built the V13 register: ASVS 13.4.1 (L1).**
Source-control metadata must not be deployed or reachable, externally or by the application itself. It is
**L1**, so it sits inside the mandatory target, and it lands on both artefacts — the static bundle
directory and the jar. Not this ticket's decision; assigned to ticket 20 or 25, whichever owns the
deployment artefacts, and flagged so ticket 18 does not find an L1 requirement with no owner.

**Currency note.** SP 800-57 Part 1 **Rev 5 remains final**; Rev 6 exists only as an initial public draft
and CSRC still lists it as Draft. Every SP 800-57 citation on this map means Rev 5. *Consolidated into the register (ticket 33): R-CFG-016. Amend the table by ID, not this list.*

### 14. Amendments owed to other tickets

- **[05](05-spring-security-7-config-surface.md):** `app.cors.allowed-origins` is superseded by
  `app.origins.spa`; CORS configuration derives from it rather than binding its own property.
- **[09](09-lockout-and-dual-rate-limiting.md):** `source.ip.hash` gets its **own** key,
  `app.security.hmac.log.key`. "No new key and no new argument" is withdrawn, and "can be reasoned about
  independently at rotation" is corrected — domain prefixes buy cross-correlation resistance, not
  independent rotation.
- **[10](10-credential-flows.md):** the link-origin property is `app.origins.spa`, shared with CORS.
- **[11](11-admin-module-role-model-and-bootstrap.md):** the tombstone HMAC key **rotates forward**;
  "cannot rotate, at all" becomes "versions accumulate and never retire". A version column is added.
- **[12](12-data-model-reconciliation.md):** a key-version column on the tombstone table; the non-default
  datasource user.
- **[20](20-deployment-origin-topology.md):** `.env.production`'s API origin is a placeholder under §11,
  with an allowlist assertion and a still-a-placeholder assertion.
- **[23](23-totp-enrolment-stepup-and-reset-flows.md):** `app.mfa.totp.encryption.key` length validation
  moves **out** of Bean Validation into the `@Bean` factory, because Boot's failure report echoes the
  rejected value.
- **[23](23-totp-enrolment-stepup-and-reset-flows.md):** additionally, `app.mfa.totp.encryption.key-version`
  has a **dashed leaf segment**, so its documented environment spelling is
  `APP_MFA_TOTP_ENCRYPTION_KEYVERSION`, not `..._KEY_VERSION`. It is required with no default, so an
  operator will meet it. Either rename to `app.mfa.totp.encryption.key.version` for consistency with
  §8.1, or document the run-together spelling. Recommend the rename. *Consolidated into the register (ticket 33): R-CFG-009. Amend the table by ID, not this list.*
- **[25](25-operational-handover-document.md):** the source-of-record-versus-override sentence (§4); the
  generation commands and the `Get-Random` warning (§7); the rotation *procedures* for all three keys;
  the datasource residuals (§10); **who performs the frontend build** (§15); and one line for whoever
  analyses logs across a restart — **a log-key rotation leaves no marker in the log stream itself.** The
  hash values simply stop correlating, and the only record that a rotation happened is the startup
  fingerprint line. That is a third distinct use for the fingerprint, alongside present-but-wrong and
  shadowed keys.

### 15. The production API origin: build-time substitution

`.env.production` ships a placeholder API origin, and `VITE_*` values are **baked into the bundle at build
time**. So the origin cannot be supplied to a built artefact; it is substituted at build.

**Runtime configuration is structurally unavailable, not merely unchosen, and ticket 20 already established
why.** [Ticket 20](20-deployment-origin-topology.md) found that CSP **intersection semantics** mean a meta
tag carrying `default-src 'self'` without the API origin in `connect-src` blocks every API call. So
`connect-src` cannot be moved to a host-owned header while the meta tag exists — the meta's `default-src`
fallback would block the calls the header permits. That closes the one route by which the CSP half could
have avoided build-time substitution. Cited rather than re-derived.

**The argument against runtime configuration leads with injection, not latency.** A runtime-fetched API
origin means the SPA's base URL comes from a file read at request time, so anything that can write or
poison that file redirects every API call, including credentialed ones. Baking it at build removes the
class outright. An extra round trip before the first API call is a footnote beside that. *Consolidated into the register (ticket 33): R-BLD-012. Amend the table by ID, not this list.*

Two consequences, both landing in ticket 25:

1. **Who performs the substitution.** With no CI in scope, `npm run build` runs on a developer's machine,
   so the deployed bundle's API origin *and* its CSP come from whatever `.env.production` existed locally
   at build time. **The build environment is part of the security configuration surface.** That is the
   statement an operator needs — sharper than "a rebuild is required", because it names who can change the
   policy and where. *Consolidated into the register (ticket 33): R-BLD-012. Amend the table by ID, not this list.*
2. **Build-once-deploy-many does not hold.** The built bundle is environment-specific, so any process that
   promotes an artefact from staging to production is invalid. That is a real deviation from ordinary
   deployment practice and it belongs beside the rebuild line rather than being discovered. *Consolidated into the register (ticket 33): R-BLD-012. Amend the table by ID, not this list.*

**Made verifiable rather than procedural**, which is the move the rest of this ticket makes everywhere
else. The emitted `index.html` is asserted after build to contain **no `%VITE_` placeholder** and **no
`localhost` in `connect-src`**. That converts the riskiest step in the chain — a human running a build with
the right env file — from a prose instruction into a check. It pairs with §11's two assertions on the
committed file: keys match the allowlist, and the committed origin is still a placeholder. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-BLD-009. Amend the table by ID, not this list.*

`VITE_*` values are **not confidential** — they ship in the bundle by design — stated plainly so nobody
later treats the frontend env files as secret material and reopens §11.

---

## Amendment from ticket 12 (data model reconciliation) — a justification for the tombstone HMAC key's rotation exception

**This ticket and ticket 12 resolved concurrently, so this is purely additive: nothing below corrects anything above.**
Everything this ticket settled about the key stands — property name `app.security.hmac.tombstone.key`, padded Base64,
exactly 32 decoded bytes, strict decoder, fail-fast on absence or malformation, distinct material from the TOTP key,
generate-on-first-run rejected, and the file-per-secret mount. The 32-byte key pairs consistently with ticket 12's
`deleted_users.email_hmac VARCHAR(64)`, which is hex of a 32-byte HMAC-SHA256 output.

**What ticket 12 adds is the justification, not the constraint.** This ticket already carries the non-rotatability
correctly, via ticket 11's handoff, as "the **second** entry in the category the ticket already opened with the password
pepper", and correctly distinguishes it from the pepper on the ground that the pepper was declined and therefore never
exists while this key exists and is permanently frozen. The gap is narrower and specific: the rotation policy's
instruction to "scope itself to keys that can rotate" **records** an exception without **defending** one, and IM8's
crypto controls generally expect a rotation capability — so an exception with no stated reason reads at review as an
omission rather than a decision.

**The exception's substance.** This key is a **blinding key for a pseudonymised reuse index, not a confidentiality key
protecting a secret at rest.** Its compromise discloses only that a given address once held an account, and only to
someone who already holds the tombstone table. Rotation exists to bound the damage of key compromise over time; here
that damage is one bit per address. This matches accepted blind-index practice, where rotation requires re-deriving
from plaintext deliberately no longer held. Note the asymmetry with ticket 07, because it is what makes the two
decisions consistent: a pepper protects a **credential**, so unbounded-in-time compromise is intolerable; this key
protects a **one-bit fact**, so the same constraint is accepted explicitly.

**Three requirements travel with it.**

1. **It MUST be a distinct key from ticket 19's AES-GCM TOTP secret key.** One key, one purpose — a shared key would
   drag the TOTP key, which rotates yearly by design, into the same non-rotatability. This ticket already says separate
   property and separate material; the reason is now recorded on the rotation side too.
2. **Because it can never rotate, its compromise is unbounded in time**, so its handling carries more weight than for
   keys that can: environment-supplied, never logged, never in a properties file, and **absence fail-fast at refresh**,
   since the failure mode is silent fail-open on reuse blocking.
3. **Pre-empt the HKDF suggestion.** Deriving both keys from one root does **not** restore rotatability: the email
   subkey would have to stay pinned to root v1 forever, which means keeping root v1 alive forever. Worth writing down
   before someone proposes it as a fix. *Consolidated into the register (ticket 33): R-CFG-014. Amend the table by ID, not this list.*

**On forward-only versioning, this ticket is right and ticket 12 was wrong.** Ticket 12's draft rejected it as "add a
key, not rotation… to buy nothing IM8 asks for". That dismissal does not survive this ticket's argument: accumulating
versions gives **a leaked key a forward response where there was none**, and turns ASVS 11.2.2 (L2) from a clean failure
into a partial. Ticket 12's rejection is withdrawn and the correction is recorded there.

**The schema consequence is nil, which is the one thing worth adding here.** Forward-only versioning of `email_hmac`
needs **no key-version column on `deleted_users`**, because an HMAC used for **equality search** is not an HMAC used for
decryption. With the TOTP secret the stored version is load-bearing — you cannot decrypt without knowing which key to
use. With the tombstone the reuse check computes the candidate's HMAC under **every live version** and looks each up
against `ux_deleted_users_email_hmac`; it never needs to know which version produced a stored row, so a version column
would be write-only information, and ticket 12's rule is not to add a column nobody reads. Cost is N lookups per
registration where N is the number of live versions, currently one. The unique index is unaffected: the same address can
only ever be tombstoned once, since reuse blocking prevents it being re-registered in between.

**Reopening trigger, exact:** if tombstone retention ever becomes **bounded**, old versions become genuinely
**retirable** at the end of the retention window — that being the moment the rows holding them stop existing, which is
the only thing that converts forward-only into full rotation. Retention is currently indefinite per ticket 11. *Consolidated into the register (ticket 33): R-CFG-003. Amend the table by ID, not this list.*

Two smaller inputs. Ticket 12 takes **Hibernate's dialect default type mappings rather than pinning them** with
`hibernate.type.preferred_*_jdbc_type`, on the reasoning that a global type pin is invisible from the schema and
silently retypes every mapping in the application — the same "configuration that cannot be seen where it matters"
concern this ticket applies to secrets. And the H2 JDBC URL carries `;LOCK_TIMEOUT=1000` pinned explicitly rather than
inherited, which is a configuration value this ticket's datasource discussion should not contradict.

---

## Amendment from ticket 13 (audit event catalogue)

[Build the audit event catalogue](13-audit-event-catalogue.md) adds three entries to this ticket's
registers and one constraint to its key table.

**1. `app.security.hmac.log.key` rotation is no longer free.** §8.1 recorded "Rotation: freely —
correlation need not span a rotation". Qualified: rotation **breaks source correlation across the
boundary**, because `source.ip_hash` exists so that repeated traffic from one source can be linked
without an address in the log stream. So the interval is pinned at **≥ the investigation window**, or the
previous key is retained for verification. The failure mode is why this is a constraint rather than a
note: a key rotating faster than the window destroys the field's only purpose while every row still
carries a plausible hash, so nothing looks wrong. *Consolidated into the register (ticket 33): R-CFG-014. Amend the table by ID, not this list.*

The field is also **renamed**: `source.ip.hash` is prohibited, since ECS types `source.ip` as `ip` and
this schema types it `string` — either way a sub-field makes it an object where every other platform
producer emits a scalar, which is dropped documents. Use **`source.ip_hash`**. The key's scope is
unchanged: `source.ip_hash` and `session.hash`, domain-prefixed.

**2. A prohibited configuration that cannot join the refresh-phase validator.** Jackson's
`StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION` must be explicitly disabled, because when enabled a
malformed request body is included in the parse exception's message — and `.setCause(e)` auto-populates
`error.message` and `error.stack_trace`, so the submitted password could reach the audit stream with no
call site naming it. It is a `JsonMapper.Builder` setting, **not a Spring property**, so the nine-entry
refresh-phase validator cannot assert it. It is asserted instead by a test on the mapper bean, owned by
ticket 13's negative-assertion set. Recorded here because recording *where a control cannot live* is what *Consolidated into the register (ticket 33): R-AUD-008. Amend the table by ID, not this list.*
stops a later session assuming it did. *Consolidated into the register (ticket 33): R-AUD-008. Amend the table by ID, not this list.*

Configuration shape under Boot 4: a `JsonMapperBuilderCustomizer` calling
`builder.disable(tools.jackson.core.StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)` — `JsonMapper` is
immutable in Jackson 3 and the packages are `tools.jackson.*` except `jackson-annotations`. The test
asserts the **outcome**, not the flag: a real malformed credential-bearing request whose resulting
message contains `REDACTED` and does not contain the submitted password. That survives a Boot upgrade
reshaping the builder API, which is a live risk given ticket 03's existing non-public
`StructuredLogEncoder` coupling. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-AUD-018. Amend the table by ID, not this list.* *Consolidated into the register (ticket 33): R-AUD-008. Amend the table by ID, not this list.*

**3. `spring.jackson.use-jackson2-defaults` was checked as a prohibited entry and is not one.** It
aligns the auto-configured mapper with Jackson 2's defaults **as of Spring Boot 3**, and Boot 3 shipped
Jackson ≥ 2.16, where source inclusion is already disabled — so the property cannot reach back past the
2.16 change. Its documented effects are serialization-side (alphabetical property sorting, dates as
timestamps). Recorded because it is the obvious property an implementer reaches for, and its absence
from this register would read as an oversight. Caveat: the source says "align as closely as possible"
rather than enumerating, so the conclusion rests on the 2.16 default being part of Boot 3's baseline. *Consolidated into the register (ticket 33): R-CFG-018. Amend the table by ID, not this list.*

**4. One addition to the startup fingerprint row.** §7's one-INFO-line-per-key fingerprint is now part
of a single mandated startup audit event (`application-startup`, which §3.1 also requires carry
`host.name` and `host.ip`) rather than standalone lines, and that row additionally records **which
audit-relevant loggers were in force and under which profile**. This matters for the reset-link
containment: ticket 13 confines the stub's link to a `dev`-only logger, and the check the refresh-phase
validator structurally cannot perform is `LOGGING_LEVEL_…=DEBUG`, which binds to no
`@ConfigurationProperties` class. That gap is closed by an `ApplicationReadyEvent` check reading the
effective level through Boot's `LoggingSystem.getLoggerConfiguration(...)`, plus `/actuator/loggers`
being absent or read-only outside `dev`. The startup row is evidence; those three are the controls.

**One confirmation.** §7's rule that a failure message reports "observed length only, never the value,
never the origin" is adopted verbatim into ticket 13's negative-assertion set, and extended: rejected
configuration values are never logged at any level, on any appender, including via a throwable. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-AUD-022. Amend the table by ID, not this list.*

---

## Amendment from ticket 21 (observability signals)

Three things land here, one of which is a counterexample to this ticket's own central idiom. All verified in
[the verification asset](../research/boot-4.1-actuator-observability-and-nist-throttling-verification.md)
§5, §9, §10, §11.

**1. A tenth prohibited configuration, and the refresh-phase validator cannot express it.** Ticket 03's
`spring-boot-starter-opentelemetry` makes OTLP *metrics* export active with **no configuration at all**,
publishing to `http://localhost:4318/v1/metrics` every 60 seconds. The only lever that stops it is
`management.otlp.metrics.export.enabled: false` (or `management.defaults.metrics.export.enabled: false`),
which removes the auto-configuration through `@ConditionalOnEnabledMetricsExport("otlp")` before any adapter
exists. This is an **assert-a-value-is-present** entry rather than this ticket's usual
assert-a-name-is-absent shape, so it does not fit the four-spelling name-absence rule and needs its own
assertion. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-OBS-012. Amend the table by ID, not this list.*

**2. This ticket's fail-fast idiom does not transfer, and the reason generalises.** The idiom here —
`@Validated @ConfigurationProperties`, no default anywhere, absence converted into failure — works for
secrets because absence binds `null` and the constraint fires. It **fails** for the OTLP url because absence
binds `null` and *Micrometer substitutes a default one layer below Boot*. Worse, the obvious workaround is a
trap: `management.otlp.metrics.export.url: ${OTLP_METRICS_URL}` with the variable unset does **not** fail
startup, because `PropertySourcesPlaceholdersResolver` builds its `PropertyPlaceholderHelper` with
`ignoreUnresolvablePlaceholders = true`, so the binder stores the **literal string** `${OTLP_METRICS_URL}` —
which `@NotBlank` accepts, and which `OtlpConfig.validate()` accepts too, since `checkRequired` is a null
check with no URL syntax validation. The result is a startup INFO line advertising the unresolved literal and
a WARN once a minute.

Note the path dependence, worth carrying as a general rule for this ticket's whole design: `@Value("${...}")`
and `Environment.getProperty` **do** throw on an unresolvable placeholder (`PlaceholderConfigurerSupport` and
`AbstractPropertyResolver` both default their ignore flags to `false`); the `@ConfigurationProperties` Binder
is the one path that does not, because it reads raw `PropertySource` values through its own lenient helper.
Every fail-fast claim on this map that rests on `@ConfigurationProperties` binding should be read against
that.

The mechanism that genuinely fails refresh for a *required* value is
`ConfigurablePropertyResolver#setRequiredProperties(...)` from an `EnvironmentPostProcessor`:
`AbstractApplicationContext#prepareRefresh()` calls `validateRequiredProperties()` before the bean factory is
touched, and it catches both shapes — absence gives `MissingRequiredPropertiesException`, and an unresolvable
placeholder gives `PlaceholderResolutionException`, because it reads through the strict `getProperty` path.
That is the right tool for the profile that *enables* export. *Consolidated into the register (ticket 33): R-CFG-019. Amend the table by ID, not this list.*

**3. `management.otlp.metrics.export.headers.*` is a credential sink and belongs in the inventory.** It is
the documented mechanism for an OTLP backend's authorization header, with env-var fallbacks
`OTEL_EXPORTER_OTLP_HEADERS` and `OTEL_EXPORTER_OTLP_METRICS_HEADERS` in `key=value,key2=value2` form. So
adopting metrics push adds a **fourth secret** to this ticket's three, conditional on a deployer configuring
export — and it is a secret whose property namespace is a map, which the name-absence assertion must handle. *Consolidated into the register (ticket 33): R-CFG-020. Amend the table by ID, not this list.*

**One more egress fact for the same entry.** `ConnectionDetails` beans do not merely outrank the property —
the adapter reads the bean and never reads the property directly, so a Docker Compose or Testcontainers
service connection (images `otel/opentelemetry-collector-contrib`, `grafana/otel-lgtm`, or an
`LgtmStackContainer` matched by type) **deletes the only path to the configured URL** for logs, metrics and
traces alike. Any assertion that telemetry goes where we said must therefore be on absent beans
(`OtlpMeterRegistry`, `OtlpConfig`, `OtlpMetricsConnectionDetails`), not on the resolved url, which is never
null and reads as configured even when nothing was configured. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-OBS-012. Amend the table by ID, not this list.* *Consolidated into the register (ticket 33): R-CFG-020. Amend the table by ID, not this list.*

---

## Amendment from ticket 09 (§R, the ticket 21 reopening) — an eleventh prohibited configuration, and a plaintext credential on a new channel

**Ticket 09 §R.3 introduces the first component in this build that emits a plaintext credential outside the HTTP
response: an operator-invoked rebinding runner (`--rebind=<username>`, scoped `password|totp|both`, with a batch
form) that mints a single-use token through ticket 10's machinery and prints it once.** It exists because ticket 13
confines the stubbed reset link to a `dev`-only logger, so outside `dev` there is no recovery artefact at all and the
NIST §3.2.2 cap would otherwise be unenforceable-in-honesty — the alternative, gating cap enforcement on a working
recovery channel, was rejected as a declined `SHALL` in better wording.

**The entry: the token prints to `System.out` and never through the logging system.** Routed through a logger it
lands in whatever appender the profile configures, which is a fourth door into precisely what ticket 13 spent three
controls closing — and unlike the reset link, this one is reachable in **every** profile, because the runner exists
outside `dev` by design. So the prohibition is on any configuration that would place the runner's output on a
logging channel, and the assertion is duplicated deliberately: here as a prohibited-configuration entry, and on
ticket 13's absolute negative list.

**This is a `System.out` write rather than a property, so the refresh-phase validator cannot express it** — the same
shape as your Jackson `INCLUDE_SOURCE_IN_LOCATION` entry, which you already recorded as an entry that "cannot join
the refresh-phase validator". It is asserted by a test on the runner rather than by configuration. Recording *where a
control cannot live* is the discipline you named; this is its third instance.

**Two smaller consequences:**

- **Your `LoggingSystem.getLoggerConfiguration(...)` check gains a second beneficiary.** It exists for the
  reset-link containment; it now also guards the one channel the runner must never reach, because
  `LOGGING_LEVEL_…=DEBUG` is the path your refresh-phase validator structurally cannot see.
- **No new key and no new secret.** The runner mints a token through existing machinery, so your three-key inventory
  and the algorithm inventory backing ASVS 11.1.2 are unchanged. Worth stating, since a new credential-emitting
  component is exactly where a fourth key would otherwise be assumed.

---

## Amendment from ticket 25 (operational handover document)

**Three corrections, one of which converts a declared prohibition into an enforced check for one line.**

**1. §7's "Why 32" row implies a floor that does not exist.** The row rests on ASVS Appendix C grading AES-128 as
**Legacy**, which is correct — but **AES-192 is graded Approved**, so as written the row reads as "anything below
32 bytes is Legacy", and that is false. Add the clause. The decision does not change: 32 bytes stays.

Recorded so it is not re-derived: a **stronger-looking** grounding was checked and **declined**. Appendix C's
key-wrapping section does mandate "AES-256 MUST be used for key wrapping, following [NIST SP 800-38F]", and
13.3.1's enumeration explicitly includes "keys and seeds for time-based tokens" — but Appendix C's approved
key-wrapping modes are **KW and KWP only**, and our TOTP secret uses **AES-GCM**. Citing the key-wrapping mandate
to obtain a number we already have would volunteer a *mode* finding on the one crypto path ticket 19 kept inside
Spring Security after CVE-2026-47842. Its carve-out is also wider than it first reads — "AES-192 and AES-128 MAY
be used if the use case demands it, but its motivation MUST be documented" — two conditions, not one. *Consolidated into the register (ticket 33): R-CFG-017. Amend the table by ID, not this list.*

**2. 13.4.5 (L2)'s verdict stands; its stated reason is wrong.** §11 records it **S** partly on an "authenticated
base path", while ticket 21 records `/actuator/health` as `permitAll` and grades that against as-13 and ac-1.
13.4.5's text is "not exposed unless explicitly intended" and ticket 21 *did* intend it, so the **S** survives on
the intent clause. Strike the authenticated-base-path clause rather than the verdict, so a reviewer comparing the
two tickets does not find a contradiction under a Satisfied row. *Consolidated into the register (ticket 33): R-OBS-011. Amend the table by ID, not this list.*

**3. The `git.properties` prohibition belongs on the jar test, not in the prohibited-configuration list.** Ticket
21 line 246 already decided this project generates neither `git.properties` nor `build-info.properties`, so a
default `info` endpoint is empty — but **four sentences later the same passage recommends adding the build-info
file, "which is mild"**, which is the reopening trigger written into the argument it invalidates. A *Consolidated into the register (ticket 33): R-BLD-015. Amend the table by ID, not this list.*
prohibited-configuration entry in this ticket's refresh-phase validator **cannot see a Maven plugin addition** —
that is a build artefact, not a bound property — so prohibition alone would land `procedural` on a row that can be
`enforced` for free. Both filenames go onto the **13.4.1 jar-content test** instead, one line. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-BLD-004. Amend the table by ID, not this list.* *Consolidated into the register (ticket 33): R-BLD-013. Amend the table by ID, not this list.*

Precision inherited with it: **13.4.1 (L1) is disjunctive** — "either without any source control metadata ... or in
a way that these folders are inaccessible both externally and to the application itself" — where §12 renders it
conjunctively. We take the first branch. And the runtime git-metadata exposure is **13.4.6 (L3)** plus 13.4.5 (L2),
**not** 13.4.1, because `git.properties` is derived from `.git` rather than being the folder 13.4.1 names. *Consolidated into the register (ticket 33): R-BLD-013. Amend the table by ID, not this list.*

**4. Two ASVS citation-hygiene notes for the V11/V13 register.** ASVS 5.0 uses **no RFC 2119 modals** — every
requirement is phrased "Verify that…" — so register rows must not back-translate ASVS into SHALL. And **ASVS V6.5's
prose cites NIST 800-63-3**, not -4, so ASVS references must not be cross-walked onto 63B-4 section numbers. Also
note the 112-bit threshold in ASVS 6.5.2 is **hard-coded**, where NIST §3.1.2.2 points at "the latest revision of
[SP800-131A]" with 112 bits as a time-stamped parenthetical — a pointer, not a constant. *Consolidated into the register (ticket 33): R-STD-035. Amend the table by ID, not this list.*

---

## Amendment from ticket 28 — the eleventh entry replaced, three validator entries, four prohibited implementations

**1. The eleventh prohibited-configuration entry becomes vacuous, and is replaced.** It prohibited any
configuration that would put the runner's `System.out` token on a logging channel. [Ticket 28](28-out-of-band-privileged-channels.md)
inverted the channel, so the runner reads the new password from stdin and **emits no secret**. The entry is
replaced by the content test in ticket 16 (a known password appears in no stream).

**2. Three new refresh-phase validator entries.** All three are visible to the validator, since two are URL
parameters and one is a bean:

| Entry | Reason |
|---|---|
| `AUTO_SERVER=TRUE` in the resolved JDBC URL | opens a random-port listener accepting remote connections, authorised by possession of `.lock.db` |
| `FILE_LOCK=NO` in the resolved JDBC URL | switches off the single-writer lock that is ticket 28's app-stopped interlock, silently, and exposes the file to concurrent writers |
| any H2 TCP server bean | H2's documented Spring example passes `-tcpAllowOthers`, and H2's admin can `CREATE ALIAS` onto arbitrary Java, which is your datasource user *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-CFG-012, T-CFG-013, T-CFG-014. Amend the table by ID, not this list.* *Consolidated into the register (ticket 33): R-CFG-021. Amend the table by ID, not this list.* |

**Fourth instance of "where a control cannot live":** `h2.bindAddress` is a JVM system property the validator
cannot see. It is moot once the TCP server bean is prohibited. *Consolidated into the register (ticket 33): R-CFG-021. Amend the table by ID, not this list.*

**3. Four prohibited implementations** on the runner, each tested by ticket 16:
1. `System.console()` nullness used to choose a mode or detect a terminal. Its behaviour changes at JDK 22, and
   `Console.isTerminal()` is the forward migration.
2. `process.command_line` or `process.args` on any audit row.
3. `System.getProperty("user.name")` as an identity fallback.
4. Any secret-bearing argument (CWE-214). *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-RUN-012, T-AUD-029, T-RUN-001, T-RUN-013. Amend the table by ID, not this list.*

**4. A requirement on your datasource row, not a change to it.** Runner mode must pass through the full context
refresh so your in-memory-URL prohibition executes. A runner that builds a `DataSource` directly reopens exactly
the case that entry closed. Runner mode additionally appends `IFEXISTS=TRUE`, because a wrong *file* path passes
your entry (it is H2-file) and H2 creates an empty database on connect.

## Amendment from ticket 31 (IPv6 source keying)

- **Inventory row `24:715`:** the `ip:` input is now the source key, `ip:4:<hex>` or `ip:6:<hex>/<n>`, not the
  address ([ticket 31](31-ipv6-source-keying.md) §5).
- **`app.security.hmac.log.key` gains an operator use.** Ticket 31 §6 route (a) hashes edge-log addresses under it to
  join them exactly to `source.ip_hash`. The deployer supplies the key as a mounted secret, so the deployer holds it,
  and the join is a controlled one-off rather than a standing script. Rotation breaks that join across the boundary,
  which is `24:871`'s existing constraint. *Consolidated into the register (ticket 33): R-CFG-014. Amend the table by ID, not this list.*
- **New non-secret property, not on your secrets inventory:** `app.security.client-ip.ipv6-prefix-length`, default 64,
  refresh-validated to [48, 128].

---

## Amendment from ticket 32 (test-plan table transcription)

The §9 prohibited-configuration table is missing one entry: **the dev-only reset-link logger's property being set outside `dev`**. Ticket 13 requires it (13:632–633), and ticket 25 counts it among its three enforcement points (25:329). The table at 24:531–544 lists neither, and 24:912–916 speaks only to `LOGGING_LEVEL_…`, which is a different path (T-CFG-010).

T-CFG-009 tests this entry. Add the entry to §9 with 13:632 as its reason, so the validator the test exercises is the one this ticket specifies.
