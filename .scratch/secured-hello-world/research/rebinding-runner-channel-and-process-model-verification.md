# Verification asset — ticket 28: the rebinding runner's process model and output channel

Facts checked against primary sources for [ticket 28](../issues/28-out-of-band-privileged-channels.md).
Per the map's verification rule, an argument resting on an external fact is not recordable until the fact is
checked at source. Each entry records **what was claimed**, **what is true**, and **which direction the error
favoured**.

---

## 1. H2 embedded file mode is single-writer, so a second JVM cannot open the database

**Source:** H2 official documentation, *Features* → "Automatic Mixed Mode" and the URL-settings table
(<https://h2database.com/html/features.html>).

Verbatim, from the "Automatic Mixed Mode" section:

> Multiple processes can access the same database without having to start the server manually.
> To do that, append `;AUTO_SERVER=TRUE` to the database URL.

and

> Internally, when using this mode, the first connection to the database is made in embedded mode, and
> additionally a server is started internally (as a daemon thread). If the database is already open in another
> process, the server mode is used automatically. The IP address and port of the server are stored in the file
> `.lock.db` [...] The first connection automatically starts a server on a random port. This server allows
> remote connections, however only to this database (to ensure that, the client reads `.lock.db` file and sends
> the random key that is stored there to the server).

and

> If the first connection is closed (the connection that started the server), open transactions of other
> connections will be rolled back.

The URL-settings table also documents an explicit lock mode: `jdbc:h2:<url>;FILE_LOCK={FILE|SOCKET|FS|NO}`.

**Consequence for this map.** Ticket 24 prohibits an in-memory resolved JDBC URL and requires H2-**file**; ticket 12
owns the schema on that runtime. A plain `jdbc:h2:file:…` database is therefore held by the running
application, and `AUTO_SERVER=TRUE` is the *documented* prerequisite for a second process to attach. Nothing on
this map sets it.

So **the runner as specified — a separate `java -jar app.jar --rebind=<username>` process — cannot open the
database while the application is running.** Neither ticket 09 §R.3, ticket 24's eleventh prohibited-configuration
entry, ticket 25 §4, nor ticket 28 mentions this. This is the ticket-11-`ImmutableSecurityHandler` class of
finding: a control that does not execute.

Note also that `AUTO_SERVER=TRUE` is **not** a free remedy: it opens a listening TCP socket on a random port that
accepts remote connections, authenticated by possession of a key written into `.lock.db`. That is a new network
surface on the one process holding every credential in the system, and it would be a candidate
prohibited-configuration entry rather than a fix.

## 2. `System.console() == null` is a valid terminal test on Java 21 and a silent fail-open from Java 22

**Source:** OpenJDK Quality Outreach heads-up, *JLine is now the Default Console Provider*
(<https://inside.java/2023/07/31/quality-heads-up/>), referencing **JDK-8308591**.

Verbatim:

> In JDK 22, `System.console()` has been changed to return a Console with enhanced editing features that improve
> the experience of programs that use the Console API. In addition, `System.console()` now returns a Console
> object when the standard streams are redirected or connected to a virtual terminal. Prior to JDK 22,
> `System.console()` instead returned null for these cases. This change may impact code that checks the return
> from `System.console()` to test if the JVM is connected to a terminal. If required, the
> `-Djdk.console=java.base` flag will restore the old behavior where the console is only returned when it is
> connected to a terminal. Starting JDK 22, one could also use the new `Console.isTerminal()` method to test if
> the console is connected to a terminal.

**Consequence for this map.** The stack baseline is **Java 21** (Temurin 21.0.9), and Boot 4.1's baseline is Java 17.

- On Java 21, `System.console() != null` **is** a working "am I on a terminal" test — so ticket 28's TTY-only
  candidate is implementable today.
- `Console.isTerminal()` — the correct API — **does not exist on Java 21** and cannot be compiled against.
- From Java 22 the same idiom **returns non-null under redirection**, so a TTY guard written the only way Java 21
  permits **fails open on a JDK upgrade**: it would permit the credential write into exactly the redirected pipe
  the guard exists to refuse. Nothing fails, nothing logs, and the guard still reads as present in the source.

This is the same failure shape the map has now hit repeatedly — ticket 23's `setMfaEnabled` and
`SecurityContextRepository` traps, ticket 21's lenient `${VAR}` placeholder, ticket 26's
`DefaultCookieSerializerCondition` — a control that silently stops applying when a neighbouring default moves.

Second-order note, **unverified and not relied on**: the release note says pre-22 behaviour returned `null` both
for redirected streams *and* when "connected to a virtual terminal". Which terminals fall in that second class was
not established at source, so no argument here rests on it. It is recorded only because it bears on whether a
Java 21 TTY guard could also refuse a *legitimate* operator, which for a break-glass tool is an availability
failure rather than a cosmetic one.

## 3. Spring Boot property precedence — ticket 28's stated reason for argument-gating is wrong; the mechanism is right

**Source:** Spring Boot reference, *Externalized Configuration*
(<https://docs.spring.io/spring-boot/reference/features/external-config.html>).

Boot's `PropertySource` order, later overriding earlier, is: default properties; `@PropertySource`; config data
(`application.properties`); `RandomValuePropertySource`; **OS environment variables**; Java system properties; JNDI;
`ServletContext` / `ServletConfig` init parameters; `SPRING_APPLICATION_JSON`; **command line arguments**; test
`properties` attribute; `@DynamicPropertySource`; `@TestPropertySource`; devtools global settings.

And on relaxed binding of placeholders:

> `${demo.item-price}` will pick up `demo.item-price` and `demo.itemPrice` forms from the
> `application.properties` file, as well as `DEMO_ITEMPRICE` from the system environment.

**Two corrections to ticket 28 finding 4.**

1. Command line arguments are **not** "the highest-precedence property source" — four sources rank above them
   (three test-only, one devtools). They are the highest-precedence source *reachable in production*, which is a
   different and weaker claim.
2. More importantly, precedence is **the wrong axis** and the ticket's argument is inverted by it: environment
   variables rank *below* command-line arguments, so a leftover `REBIND` could never override an explicit
   `--rebind`. The real mechanism is that env vars sit in the **same lookup namespace** — a property read for
   `rebind` is satisfiable by `REBIND` in the environment, exactly as the relaxed-binding rule above states. So
   property-gating admits a trigger the operator never typed.

The finding therefore survives, but it is **two distinct paths**, which ticket 28 conflates into one:

- **A property lookup** (`Environment.getProperty`, `@Value`, `@ConfigurationProperties`) is satisfiable by an
  environment variable, so `REBIND=alice` in a unit file or container spec fires the runner with no argument
  present. Closed by reading `ApplicationArguments`/`getOptionNames()` instead of the `Environment`.
- **A leftover literal `--rebind=alice` in a persistent deployment spec** (unit file `ExecStart`, container
  `args:`, Compose `command:`) re-fires on **every restart** regardless of binding mechanism, because the spec
  itself is the durable thing. Argument parsing does not close this one at all; only an interlock does
  (a confirmation, or refusing when the state it would rebind is not actually capped).

Ticket 24's adjacent finding on the same mechanism is the precedent and is correctly cited by ticket 28: a
leftover environment variable "silently wins over the mounted file you just rotated. Nothing fails."

## 4. Container log collection scopes to the main process, not to `exec`ed processes

**Status: corroborated but NOT established at a primary source.** Recorded so the distinction is not leaned on
harder than its evidence supports.

Ticket 28 asserts that "on any container runtime the collection is unconditional and is the point of the
runtime". That is right for the container's **main process** (PID 1), whose stdout/stderr the runtime captures and
`kubectl logs` / `docker logs` serve. It is **not** right for a process started by `docker exec` / `kubectl exec`,
which is a new process with its own streams attached to the exec client, not to the container's log stream.

Attempts to confirm this in the Kubernetes logging-architecture page did not retrieve the relevant text, so the
claim rests on community sources only. **Nothing in ticket 28's resolution should depend on the exec case being
uncollected** — and under finding 1 above it is close to moot anyway, since an `exec`ed second JVM cannot open the
H2 file while the application holds it.

The part that matters and is not in doubt: **whether the runner's stdout is collected is a property of how the
operator starts it**, not a property the application can observe or enforce. A one-off Kubernetes Job or an
overridden container command runs as PID 1 and **is** collected. A systemd one-shot unit is captured to the
journal and **is** collected. An interactive shell on a host with the service stopped is **not**. The application
sees the same `FileDescriptor.out` in all three.

---

## Scoreboard

Four external facts checked, **three were wrong or materially incomplete as carried on the map**, and the
directional pattern the map has recorded twice before **does not hold this time** — the errors do not all favour a
conclusion being argued:

| Fact | Claimed | Verified | Direction |
|---|---|---|---|
| H2 file mode and a second JVM | not addressed anywhere | single-writer; `AUTO_SERVER=TRUE` required, and it opens a remote-capable socket | **against** every existing position — the runner does not run |
| `System.console()` as a TTY test | offered as "enforceable, fails closed" | true on Java 21 only; fails **open** from Java 22, and `isTerminal()` is unavailable to us | **favoured** ticket 28's own candidate option |
| Command-line args as highest-precedence source | stated as the reason property-gating is unsafe | false; env vars rank lower. The real reason is shared namespace, and there is a second path argument-parsing does not close | favoured the conclusion, which nonetheless survives on a corrected mechanism |
| Container collection of stdout | "unconditional … the point of the runtime" | true for PID 1; scope narrower than stated; **not** established at primary source | favoured the premise |

---

# Round 2 additions

## 5. ASVS 6.4.1 (L1) does not foreclose an operator-chosen password — 6.4.6 (L3) does

**Source:** OWASP ASVS 5.0, V6 Authentication, §V6.4 "Authentication Factor Lifecycle and Recovery"
(<https://github.com/OWASP/ASVS/blob/master/5.0/en/0x15-V6-Authentication.md>).

Verbatim:

> **6.4.1** | Verify that system generated initial passwords or activation codes are securely randomly generated,
> follow the existing password policy, and expire after a short period of time or after they are initially used.
> These initial secrets must not be permitted to become the long term password. | **1**

> **6.4.6** | Verify that administrative users can initiate the password reset process for the user, but that this
> does not allow them to change or choose the user's password. This prevents a situation where they know the
> user's password. | **3**

**Ticket 09 §R.3 has these inverted.** It records: "ASVS **6.4.6 (L3)** is the shape — the operator initiates and
cannot choose the password — and **6.4.1 (L1)** is the binding one, because it forecloses the 'operator types a
temporary password' variant someone will propose as simpler."

That is wrong, and wrongly at the level that matters:

- **6.4.1 (L1) is conditional and says nothing about who chooses.** Its subject is "system generated initial
  passwords or activation codes" — *if* the system generates one, it must be CSPRNG, must follow the password
  policy, and must expire on first use or shortly. It contains no prohibition on an operator choosing a password.
  It cannot foreclose the variant ticket 09 says it forecloses, because that variant generates no system secret
  for 6.4.1 to govern.
- **6.4.6 (L3) is the requirement that actually forecloses it**, in as many words — and it is **L3**, above the
  declared **L1** target.

So the token design is compelled only at L3, not at L1. The error's direction matches the pattern this map has
now recorded three times: it made the chosen design look mandatory at the conformance level we actually claim.

Consequence: an operator-supplied-password variant is **admissible at L1**. 6.4.1's final clause ("must not be
permitted to become the long term password") is discharged either way by forcing a change at next login, which
ticket 11's forced-change filter already implements.

**Corrected in round 3 — the round-2 grading over-conceded.** Round 2 recorded taking the variant as "a knowing
failure of 6.4.6 (L3)". Against an **L1** target that is the ticket-09 error pointed the other way: it grades a
requirement outside the target as a failure. The accurate accounting, from this repository:

- 6.4.6 was **adopted**, not merely observed. Ticket 10 records it as "now *satisfied* … which was the main reason
  to take the invite token", and ticket 07 records it as "the reason" the 20-character admin generator was deleted.
  Ticket 15 grades it satisfied. An adoption that was load-bearing for two decisions is withdrawn by a **register
  entry**, not a note.
- The withdrawal is **scoped to one path**. The in-app admin-create and admin-reset endpoints still issue tokens and
  still satisfy 6.4.6, so neither of the two decisions it justified is disturbed. Only the break-glass runner path
  stops satisfying it, and that path sits behind host access.
- The protection was **already notional on that path**. In the token design the token printed to the *operator's*
  terminal, not the user's, so the operator could redeem it and choose the password. The inversion makes explicit a
  capability the operator always had. The control actually constraining operator abuse on this path is the audit
  row plus its alert, in both designs.

Also noted while at source, bearing on Q5's cap and on ticket 07: **6.2.4 (L1)** requires checking against "at
least, the top 3000 passwords which match the application's password policy", and **6.2.12** (breached-password
sets) is **L2**. Ticket 07's zxcvbn-3 gate plus breach-corpus blocklist clears both comfortably; recorded only so
a later reader does not think the gate was unlevelled.

## 6. H2 *can* bind its server socket to loopback — so the loopback variant needs a narrower rejection

**Source:** H2 official documentation, *Advanced* → "Setting the Server Bind Address"
(<https://h2database.com/html/advanced.html>).

Verbatim:

> Usually server sockets accept connections on any/all local addresses. This may be a problem on multi-homed
> hosts. To bind only to one address, use the system property `h2.bindAddress`. This setting is used for both
> regular server sockets and for TLS server sockets.

So `-Dh2.bindAddress=127.0.0.1` is real and documented, and the round-1 rejection ("the only option that doesn't
add a network surface") was **too strong as stated**.

**Corrected in round 3.** The round-2 draft of this rejection carried two reasons — the `.lock.db` random-key channel
and the first-connection-close rollback — that H2 documents under **Automatic Mixed Mode** only. An explicit
loopback listener is a different object (`org.h2.tools.Server.createTcpServer`), authenticated by database
credentials, not by a key in a lock file. Those two reasons are withdrawn. What replaces them was checked at source
(H2 *Advanced* → "Protection against Remote Access" and "Restricting Class Loading and Usage",
<https://h2database.com/html/advanced.html>; H2 *Tutorial* → "Using Spring",
<https://h2database.com/html/tutorial.html>):

> By default this database does not allow connections from other machines when starting the H2 Console, the TCP
> server, or the PG server. Remote access can be enabled using the command line options `-webAllowOthers`,
> `-tcpAllowOthers`, `-pgAllowOthers`.

> By default there is no restriction on loading classes and executing Java code for admins. That means an admin
> may call system functions such as `System.setProperty` by executing:
> `CREATE ALIAS SET_PROPERTY FOR "java.lang.System.setProperty";`

and H2's own Spring example for the TCP server is
`<constructor-arg value="-tcp,-tcpAllowOthers,-tcpPort,8043" />`.

So the defaults are more subtle than "fail-open": the socket binds every interface unless `h2.bindAddress` is set,
but H2 refuses non-local peers unless `-tcpAllowOthers` is passed. **The documented Spring example passes it** —
the copy-paste path is the one that opens the database to the network. The rejection, on the record:

1. `h2.bindAddress` is a **JVM system property, not a JDBC URL parameter**, so it lives on the launch command and
   is invisible to ticket 24's refresh-phase validator — a **fourth** instance of "where a control cannot live".
2. **The prescribed-looking configuration is the insecure one.** H2's own Spring example passes `-tcpAllowOthers`,
   which is the remote-enabling flag; a loopback listener depends on an implementer *not* copying it.
3. **Any local process holding the datasource password gets code execution in the JVM that holds every key.**
   Ticket 24 established that the user named on H2's first connection becomes its administrator, and H2 lets an
   admin `CREATE ALIAS` onto arbitrary Java methods by default. A listener therefore converts "can read the
   datasource password" into "can run code beside ticket 24's three keys".
4. The surface is **permanent to enable a path used approximately never**; option (a)'s equivalent exists only for
   the duration of the recovery.
5. Decisive, and sufficient alone: it is **orthogonal to the channel question**. It buys "no outage" and nothing
   else; the runner still has to decide what it emits.

## 7. CWE-214 is the right identifier for the never-`--password` constraint, and there is a close real-world instance

**Source:** MITRE CWE-214, *Invocation of Process Using Visible Sensitive Information*:

> A process is invoked with sensitive command-line arguments, environment variables, or other elements that can
> be seen by other processes on the operating system.

The closest published instance to the shape being avoided here is **CVE-2026-9494** (Ubuntu Pro Client): an
unprivileged local user on a host without `hidepid` protections can read a credential from `/proc/cmdline` while
the helper process runs. That is the same mechanism a `--password=` argument on this runner would create, on a
host where the operator is by definition not the only account.

Pairs with **CWE-532** (sensitive information in a log file) for the output half. Both are cited rather than
restated in the ticket.

## 8. Published break-glass practice: two accounts, recurring validation, post-use review

**Source:** Microsoft Entra ID emergency-access guidance
(<https://learn.microsoft.com/entra/identity/role-based-access-control/security-emergency-access>) and the
Zero Trust Assessment workshop guidance derived from it
(<https://microsoft.github.io/zerotrustassessment/docs/workshop-guidance/identity/RMI_150>).

Content rephrased for compliance with licensing restrictions. The convergent recommendations are: maintain **at
least two** emergency access accounts rather than one; keep them unassigned to specific individuals; alert on any
use; run a **post-incident review** of every use to confirm it was authorised; **test access quarterly** to
confirm the path still functions; and rotate the credentials roughly every 90 days or immediately on personnel
change. The workshop guidance states the rationale for recurrence bluntly — an untested emergency account is
worse than none, because it is believed to work.

Relevance, and the limits of it: this is *identity-platform* guidance, not an application standard, and nothing
in it binds us. It is cited for two narrow purposes only — that **one** emergency path is below common practice
(bearing on the sole-admin bootstrap question), and that a rehearsal without a **recurrence** is a known
anti-pattern (bearing on ticket 25's step 7, which this map has already proved the hard way: the runner was
unexecutable for the entire documented life of the procedure that rehearses it).

---

# Round 3 additions

## 9. The "rescue tool that lies" — ticket 24 closes the in-memory case; H2's auto-create leaves a wrong-file case open

**Repository facts.** Ticket 24 §9's refresh-phase validator already prohibits "Resolved JDBC URL is **H2
in-memory**, or is not H2-file when the dev profile is active", precisely because an unset URL otherwise resolves
`jdbc:h2:mem:<uuid>` and "Flyway then migrates that database successfully and `ddl-auto: validate` passes". Ticket 25
carries the same fact as a handover line. So the case where the runner is launched with **no** datasource
configuration is already a refresh failure — *provided the runner starts through the same context refresh as the
application*. Option (a) (same jar) gives that, and it must become a stated requirement: a stripped runner context
that skips the validator reopens the case.

**External facts** (H2 *Tutorial* and *Features*, <https://h2database.com/html/tutorial.html>,
<https://h2database.com/html/features.html>):

> [If the database does not exist,] a new (empty) database is created automatically. The user that created the
> database automatically becomes the administrator of this database. Auto-creation of databases can be disabled,
> see Opening a Database Only if it Already Exists.

> If no or only a relative path is used, then the current working directory is used as a starting point.

and the URL setting `jdbc:h2:<url>;IFEXISTS=TRUE` — "Only open if it already exists".

**What survives ticket 24's validator.** A **file** URL that resolves somewhere other than the production database —
a relative path launched from a different working directory, or a typo — passes the validator (it *is* H2-file),
auto-creates an empty database, which Flyway migrates and `validate` passes. The runner then acts against an empty
schema. Three further consequences found in this repository, none previously stated:

1. **Ticket 11's seeding `ApplicationRunner` runs in the runner process too.** Runners execute regardless of
   `web-application-type`. On the correct database it is a no-op (an `ADMIN` row exists); on an empty one it would
   **seed a fresh admin** into the throwaway database if `APP_ADMIN_*` is present in the operator's environment.
2. **Flyway would migrate the production database as a side effect of break-glass** if the runner jar is newer than
   the schema — an upgrade performed under emergency conditions, by accident.
3. The audit row still lands in the real audit file (logging does not depend on the datasource), so the evidence
   **records a rebinding that changed nothing** — which is what makes this worse than a leak.

## 10. Logback's file appender is single-writer unless prudent mode is set

**Source:** Logback `FileAppender` javadoc (re-hosted verbatim in the Adobe AEM API reference,
<https://experienceleague.adobe.com/en/tools/aem-api-documentation/6-5/javadoc/ch/qos/logback/core/FileAppender.html>):
"When prudent is set to true, file appenders from multiple JVMs can safely write to the same file." The default is
`false`. The Logback manual's own page did not return the text on retrieval, so this is a re-hosted primary rather
than the canonical URL.

Ticket 13 does not set prudent mode, and prudent mode forbids compression and constrains the rolling policy, so
it is not a free remedy either.

## 11. Boot initialises logging before the datasource — **not established at primary source**

The claim is that `LoggingApplicationListener` initialises the logging system on `ApplicationEnvironmentPreparedEvent`,
before context refresh and therefore before the H2 lock is contended. The Boot 4.1.1 API page for
`LoggingApplicationListener` confirms the class and its `initialize` method but does not render the event
association; corroboration is from community sources only. **Nothing in ticket 28's resolution depends on it.** The
control is written against the *outcome* — a runner started while the application is running must exit non-zero and
**append zero bytes** to the audit file — which is true or false by test regardless of which lifecycle phase opens
the appender. If the hypothesis holds, the test fails until the precondition is checked before logging initialises,
which is the point of writing it as a test.

## 12. Uncorroborable operator identity: `ProcessHandle.Info.user()`, not `user.name`

**Source:** Java SE 21 `ProcessHandle.Info`
(<https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ProcessHandle.Info.html>): `user()` —
"Return the user of the process", returning `Optional<String>`; "The attributes of a process vary by operating system
and are not available in all implementations."

`System.getProperty("user.name")` is a JVM system property and is reported (community sources only) to be
overridable with `-Duser.name=`. The design does not rest on that report: it rests only on `ProcessHandle` being the
OS-reported owner and `user.name` being a property. Consequence: the corroborating field is taken from
`ProcessHandle.current().info().user()`, and when that `Optional` is empty the row records **absence**, never a
fallback to `user.name` — a fallback would silently replace an OS fact with a value the operator typed.

---

# Round 4 additions

## 13. H2's single-writer lock can be switched off by URL — so the app-stopped interlock needs a validator entry

**Source:** H2 *Features*, URL-settings table (<https://h2database.com/html/features.html>), fetched in round 1:
`jdbc:h2:<url>;FILE_LOCK={FILE|SOCKET|FS|NO}`.

Ticket 28's app-stopped control (§6 check 6 of its answer) rests entirely on H2 refusing a second JVM. `FILE_LOCK=NO`
removes that refusal: two JVMs open the file, the interlock is gone, and the database is exposed to concurrent
unsynchronised writers. Nothing fails at startup. It is a URL parameter, so unlike `h2.bindAddress` it **is**
visible to ticket 24's refresh-phase validator, and it joins `AUTO_SERVER=TRUE` there. Those two entries plus the
TCP-server prohibition close, respectively: disabling the lock, and the two ways of sharing the database.

## 14. ECS placement of the operator fields

**Sources:** ECS *process* field reference (<https://www.elastic.co/docs/reference/ecs/ecs-process>) and ECS
*Custom fields* guidance (<https://www.elastic.co/docs/reference/ecs/ecs-custom-fields-in-ecs>).

- `process.real_user.*` — "The real user (ruid). Identifies the real owner of the process." `process.user.*` — "The
  effective user (euid)." Both are ECS reuse locations of the `user` field set.
- `ProcessHandle.Info.user()` (§12) documents only "the user of the process", not which uid. For a non-setuid `java`
  process ruid equals euid, so the distinction does not bite in practice; the field is named
  `process.real_user.name` with an **owed build-time check** of which uid the JDK reports on the target OS, so the
  name never claims more than the API guarantees.
- ECS's custom-field guidance names `labels` as the simplest way to carry a few extra keyword-typed values, and
  states the risk avoided is conflict with a future ECS field — the exact risk of a new top-level `operator.*`.
  Ticket 13 rejected `labels` only for `user.target.count`, because every label is `keyword` and a numeric count
  would be stripped. A staff identifier is a keyword, so the rejection does not transfer.
  **`labels.operator_claimed_id`** is therefore ECS's own recommended shape, adds no custom field, and adds none of
  the keys on ticket 13 §10's key-absence row (`user.name`, `user.hash`), so that assertion stands untouched.

---

## Final scoreboard for ticket 28

Across four rounds, **eleven** external facts or repository claims that the ticket's argument would have rested
on were checked; **seven** were wrong or materially incomplete as carried — and three of the seven were drafted in
this session, by the resolver, before being caught:

| # | Claim | Outcome |
|---|---|---|
| 1 | The runner can run beside the app | false — H2 single-writer (§1) |
| 2 | `System.console()` is a durable TTY test | true on 21 only (§2) |
| 3 | CLI args are highest-precedence | false; real mechanism is shared namespace (§3) |
| 4 | 6.4.1 (L1) forecloses operator-chosen passwords | false — that is 6.4.6 (L3) (§5) |
| 5 | Option (a) makes the output uncollected | false, drafted here — a Job or one-shot unit is collected (§4) |
| 6 | Loopback rejection via `.lock.db` and rollback | wrong mechanism, drafted here — those are `AUTO_SERVER`'s (§6) |
| 7 | "Account in capped/tier-2 state" as a precondition | refused the primary case, drafted here (ticket 25 lines 96–99) |

What the table argues is not that any one ticket was careless. It is that **four of the five findings that shaped
the resolution were found by checking premises rather than by answering the question as posed.**
