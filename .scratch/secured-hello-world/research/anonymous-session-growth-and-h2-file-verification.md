# Verification asset — ticket 29 (anonymous session-row growth and the H2 file)

Produced under the map's **verification rule** for ticket 29. Primary sources only: Spring Session `4.1.1`,
Spring Boot `v4.1.1`, Spring Security `7.1.1`, H2 `version-2.4.240`, Micrometer `v1.17.1` (Spring Session, Security,
H2 and Micrometer versions are the ones pinned in `platform/spring-boot-dependencies/build.gradle` at `v4.1.1`),
the H2 reference documentation, and three local measurements run against the released jars from Maven Central.
Every claim is **CONFIRMED**, **NUANCED** (the qualification named) or **WRONG** (the premise as asked is false).
Verbatim quotes are kept short; everything else is paraphrase.

Source links use these prefixes:

- `SS` = `https://github.com/spring-projects/spring-session/blob/4.1.1/`
- `SS-JDBC` = `SS` + `spring-session-jdbc/src/main/java/org/springframework/session/jdbc/JdbcIndexedSessionRepository.java`
- `SB` = `https://github.com/spring-projects/spring-boot/blob/v4.1.1/`
- `SEC` = `https://github.com/spring-projects/spring-security/blob/7.1.1/`
- `H2` = `https://github.com/h2database/h2database/blob/version-2.4.240/h2/src/main/org/h2/`
- `MM` = `https://github.com/micrometer-metrics/micrometer/blob/v1.17.1/`

Caveat on H2 documentation: `h2database.com/html/*` is not versioned; it documents the current release, which is
the 2.4 line Boot pins. Every H2 claim that carries an argument is also checked against the `version-2.4.240` source.

Bias check: I expected "the cleanup needs `@EnableScheduling`" and "H2 files never shrink". **Both came back
wrong.** Spring Session starts its own scheduler thread, and H2 does truncate the file, just not while idle.

---

## §A — Spring Session JDBC (`JdbcIndexedSessionRepository`, 4.1.1)

### A.1 What is INSERTed for a new session; is an attribute-less session persisted?

**CONFIRMED (one `SPRING_SESSION` row + one `SPRING_SESSION_ATTRIBUTES` row per attribute). NUANCED on the
zero-attribute case: persisted if and only if something called `getSession(true)`.**

- `createSession()` builds a `JdbcSession` with `isNew = true` (`SS-JDBC` lines 468–473). With the default
  `FlushMode.ON_SAVE` (line 250) `flushIfRequired()` is a no-op, so nothing hits the database during the request.
- At commit, `save()` with `isNew` runs, in **one transaction**, one `CREATE_SESSION_QUERY` INSERT into
  `SPRING_SESSION`, then — only `if (!attributeNames.isEmpty())` — `insertSessionAttributes` for **all** current
  attributes, one row each, batched when there are more than one (`SS-JDBC` lines 890–908; insert helper
  lines 530–585).
- The trigger is `SessionRepositoryFilter.commitSession()`: it calls `sessionRepository.save(session)` whenever a
  current session wrapper exists (`SS` + `spring-session-core/src/main/java/org/springframework/session/web/http/SessionRepositoryFilter.java`
  lines 219–236). The wrapper is created only by `getSession(true)` (lines 307–324); `getSession(false)` with no
  valid cookie returns `null` (lines 307–309).
- **Consequence.** A request that calls `getSession()` and sets nothing still writes a `SPRING_SESSION` row with
  zero attribute rows. A request that never asks for a session writes nothing. For `/api/csrf` with
  `HttpSessionCsrfTokenRepository`, `saveToken` calls `request.getSession()` then `setAttribute`
  (`SEC` + `web/src/main/java/org/springframework/security/web/csrf/HttpSessionCsrfTokenRepository.java`
  lines 58–61), so each new anonymous caller costs **1 + 1 rows**.

### A.2 `schema-h2.sql` columns, types and indexes

**CONFIRMED, with one discrepancy against the class Javadoc.** Source:
`SS` + `spring-session-jdbc/src/main/resources/org/springframework/session/jdbc/schema-h2.sql`.

| Table | Column | Type |
|---|---|---|
| `SPRING_SESSION` | `PRIMARY_ID` | `CHAR(36) NOT NULL` (PK `SPRING_SESSION_PK`) |
| | `SESSION_ID` | `CHAR(36) NOT NULL` |
| | `CREATION_TIME`, `LAST_ACCESS_TIME`, `EXPIRY_TIME` | `BIGINT NOT NULL` |
| | `MAX_INACTIVE_INTERVAL` | `INT NOT NULL` |
| | `PRINCIPAL_NAME` | `VARCHAR(100)` (nullable) |
| `SPRING_SESSION_ATTRIBUTES` | `SESSION_PRIMARY_ID` | `CHAR(36) NOT NULL` |
| | `ATTRIBUTE_NAME` | `VARCHAR(200) NOT NULL` |
| | `ATTRIBUTE_BYTES` | `LONGVARBINARY NOT NULL` |

Indexes and constraints in the H2 script: `SPRING_SESSION_IX1` **unique** on `SESSION_ID`, `SPRING_SESSION_IX2` on
`EXPIRY_TIME`, `SPRING_SESSION_IX3` on `PRINCIPAL_NAME`; PK `(SESSION_PRIMARY_ID, ATTRIBUTE_NAME)`; FK to
`SPRING_SESSION(PRIMARY_ID)` **`ON DELETE CASCADE`**. The generic DDL in the class Javadoc (`SS-JDBC` lines 106–129)
also lists `SPRING_SESSION_ATTRIBUTES_IX1`; **the H2 script does not create it.** (Whether H2 adds an implicit FK
index is not verified; the PK's leading column covers the FK lookup either way.)

### A.3 Cleanup cron: default, property, predicate, and who schedules it

**CONFIRMED on all four; the scheduling premise in ticket 21 is WRONG.**

- **Default** `"0 * * * * *"` (every minute, second 0): `JdbcIndexedSessionRepository.DEFAULT_CLEANUP_CRON`
  (`SS-JDBC` line 153); Boot repeats it in `JdbcSessionProperties.DEFAULT_CLEANUP_CRON`
  (`SB` + `module/spring-boot-session-jdbc/src/main/java/org/springframework/boot/session/jdbc/autoconfigure/JdbcSessionProperties.java`
  line 39).
- **Property** `spring.session.jdbc.cleanup-cron`: `@ConfigurationProperties("spring.session.jdbc")` (line 31),
  field `cleanupCron` "Cron expression for expired session cleanup job" (lines 46–49); applied by the
  `springBootSessionRepositoryCustomizer` (`JdbcSessionAutoConfiguration.java` line 86). `setCleanupCron` accepts
  `Scheduled.CRON_DISABLED` (`"-"`) to switch it off (`SS-JDBC` lines 459–465). (The 4.1 properties appendix page
  could not be matched by the fetch tool for this key; the source is the authority here.)
- **Predicate** `DELETE FROM SPRING_SESSION WHERE EXPIRY_TIME < ?` with `System.currentTimeMillis()`
  (`SS-JDBC` lines 206–209, 647–651), a **single statement in one transaction**; attribute rows go via the FK
  cascade. `EXPIRY_TIME` = `lastAccessedTime + maxInactiveInterval` (lines 776–781). Live rows are untouched.
- **No `@EnableScheduling` needed.** `afterPropertiesSet()` creates its **own** `ThreadPoolTaskScheduler`
  (thread prefix `spring-session-`), initialises it and schedules `cleanUpExpiredSessions` on a `CronTrigger`
  unless the cron is `"-"` (`SS-JDBC` lines 275–287); `destroy()` shuts it down (lines 290–294). So **a scheduler
  thread exists in the application by default** whenever Spring Session JDBC is on the classpath and configured.
- **Consequence for ticket 29.** Ticket 21 declined a session-row-count gauge because "it needs a scheduled query,
  and scheduled jobs are the map's largest deferral" (`21:401-402`). A scheduled job over `SPRING_SESSION`
  already runs every minute; the deferral argument does not cover this case. (Whether a gauge *should* be added is
  ticket 29's decision; this only removes one stated reason.)

### A.4 Built-in cap on total session count

**CONFIRMED: none.** The `SessionRepository` contract is `createSession`, `save`, `findById`, `deleteById`
(`SS` + `spring-session-core/src/main/java/org/springframework/session/SessionRepository.java` lines 42–71);
`createSession()` has no guard (`SS-JDBC` lines 468–473). A search of the `4.1.1` tree for `maxsession`, `limit`,
`quota` finds only the `spring-session-sample-boot-reactive-max-sessions` sample, which is a per-principal
(Spring Security) concurrent-session example, not a global cap. The only count-like controls in the Spring stack
are Spring Security's per-principal `maximumSessions`.

### A.5 `changeSessionId` on login: UPDATE of the same row, not a new INSERT

**CONFIRMED.**

- Spring Security 7.1.1's default session-fixation strategy is `ChangeSessionIdAuthenticationStrategy`
  (`SEC` + `config/src/main/java/org/springframework/security/config/annotation/web/configurers/SessionManagementConfigurer.java`
  lines 111, 625–627), which calls `request.changeSessionId()`
  (`SEC` + `web/src/main/java/org/springframework/security/web/authentication/session/ChangeSessionIdAuthenticationStrategy.java`
  line 33).
- The Spring Session wrapper routes that to `JdbcSession.changeSessionId()` (`SessionRepositoryFilter.java`
  lines 254–263), which sets `changed = true` and replaces the delegate's id only (`SS-JDBC` lines 789–794).
  `primaryKey` is final and unchanged.
- On save of a non-new session, `changed` produces `UPDATE SPRING_SESSION SET SESSION_ID = ?, … WHERE PRIMARY_ID = ?`
  (`SS-JDBC` lines 174–178, 911–926). Setting `SPRING_SECURITY_CONTEXT` also sets `changed` (line 839–841) and adds
  one attribute row via the `ADDED` delta (lines 928–936).
- **Consequence.** Login converts the anonymous row in place; it neither adds a second `SPRING_SESSION` row nor
  frees one. If a new session is created *and* its id changed in the same request, it is still one INSERT with the
  final id (the `isNew` branch reads `getId()` at save time, line 898).

## §B — Serialized size of the stored CSRF token (Spring Security 7.1.1)

**CONFIRMED, measured: 205 bytes** of `ATTRIBUTE_BYTES` per anonymous CSRF session.

- What is stored: `HttpSessionCsrfTokenRepository` stores the **raw** `DefaultCsrfToken` under
  `org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository.CSRF_TOKEN` (79 characters; lines 37–42,
  58–61, 74–76). The token is `UUID.randomUUID().toString()`, 36 chars (lines 107–109).
  `RepositoryDeferredCsrfToken` generates then `saveToken`s that raw token
  (`SEC` + `web/src/main/java/org/springframework/security/web/csrf/RepositoryDeferredCsrfToken.java` lines 66–70).
  `XorCsrfTokenRequestAttributeHandler` builds a *new* masked `DefaultCsrfToken` for the request attribute only
  (`XorCsrfTokenRequestAttributeHandler.java` lines 72–73); the masked form is **not** what the session holds.
- `DefaultCsrfToken` has three `String` fields `token`, `parameterName`, `headerName` and
  `serialVersionUID = 6552658053267913685L` (`SEC` + `web/src/main/java/org/springframework/security/web/csrf/DefaultCsrfToken.java`
  lines 29–38).
- Serializer: Spring Session JDBC's default converter is Spring's `SerializingConverter` (`SS-JDBC` lines 657–662),
  i.e. standard Java serialization.
- **Measurement.** `new SerializingConverter().convert(new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", UUID))`
  against `spring-security-web-7.1.1.jar` + `spring-core-7.0.9.jar` on JDK 21: **205 bytes**, stable over three
  runs (fixed-length strings, so it cannot vary).
- **Per anonymous session, raw column payload** (derived, excluding H2 row/index overhead): `SPRING_SESSION`
  ≈ 36 + 36 + 3×8 + 4 bytes + null ≈ 100 B; `SPRING_SESSION_ATTRIBUTES` ≈ 36 + 79 + 205 ≈ 320 B. Roughly
  **0.4 KB of data per session before storage overhead** — but see §C.1: file growth is dominated by MVStore
  behaviour, not by this number.

## §C — H2 2.4.240 MVStore

### C.1 Does the file shrink after DELETE? Compaction, `MAX_COMPACT_TIME`, `SHUTDOWN COMPACT`, retention, write delay

**NUANCED.** Space is reused while open; the file is **truncated** only when the tail of the file becomes free,
which in practice happens on writes and on close, **not** while idle.

Documented behaviour:

- H2 [Features › Compacting a Database](https://www.h2database.com/html/features.html): empty space is re-used
  automatically; on close the database is compacted for up to 200 ms by default; use `SHUTDOWN COMPACT` to
  compact more; re-creating the database may shrink it further.
- [`SHUTDOWN`](https://www.h2database.com/html/commands.html): options `IMMEDIATELY | COMPACT | DEFRAG`;
  `COMPACT` "fully compacts the database"; a normal close compacts for at most `h2.maxCompactTime`; `DEFRAG` is
  currently equivalent to `COMPACT`. Admin rights required.
- [MVStore › Log Structured Storage](https://www.h2database.com/html/mvstore.html): no in-place updates; old data
  is kept at least 45 s; the chunks with the least live data are compacted; disk usage can temporarily be higher
  than a conventional engine because space is not re-used immediately.

Settings (source-confirmed defaults, `H2` + `engine/DbSettings.java`):

| Setting | Default | Meaning | Line |
|---|---|---|---|
| `MAX_COMPACT_TIME` | 200 ms | max compaction time on normal close | 141–145 |
| `AUTO_COMPACT_FILL_RATE` | 90 (%) | background rewrite of low-fill chunks and move of end-of-file chunks; `0` disables | 55–66 |
| `DEFRAG_ALWAYS` | false | full defrag on every normal close | 109–114 |
| `REUSE_SPACE` | true | if false, append-only | 239–244 |
| `RETENTION_TIME` | 45 000 ms | how long old persisted data is kept before overwrite | [commands › SET RETENTION_TIME](https://www.h2database.com/html/commands.html); `H2` + `mvstore/FileStore.java` lines 1310–1311 |
| `WRITE_DELAY` | 500 ms | max delay between commit and flushing the log | [commands › SET WRITE_DELAY](https://www.h2database.com/html/commands.html) |

Source mechanics: after writing a chunk that is not at end-of-file, `RandomAccessStore` calls
`shrinkStoreIfPossible(1)`, which `truncate`s the file to the used length if at least 1 % would be saved
(`H2` + `mvstore/RandomAccessStore.java` lines 375–378, 676–701); the clean-shutdown mark shrinks unconditionally
(lines 414–418). Background housekeeping moves chunks toward the start when fragmented and below the fill-rate
target (lines 704–722), but `stopIdleHousekeeping` stops that loop once an idle pass makes no progress
(lines 740–746).

**Measurement** (local, H2 2.4.240 jar, Spring Session `schema-h2.sql`, one 205-byte attribute per session,
autocommit per INSERT like the repository's per-request transactions, then one bulk `DELETE` like the cleanup job):

| Step | 20 000 sessions | 100 000 sessions |
|---|---|---|
| after inserts + `CHECKPOINT` | 34.4 MB | 686.8 MB |
| after one bulk `DELETE` + `CHECKPOINT` | **109.0 MB** | **1 708.9 MB** |
| idle, open (20k: 5 min; 100k: 1 min) | 109.0 MB, no change | 1 708.9 MB, no change |
| after normal close (≤ 200 ms compaction) | 24 KB | 217 KB |
| after reopen + `SHUTDOWN COMPACT` | 12 KB | 12 KB |

A second run cycled insert-20k / delete / 50 s idle four times: the file plateaued at ~110 MB (36 → 110 → 110 →
109 → 39 → 111 MB), i.e. **space is reused and the file sometimes truncates during writes, but it holds its
high-water mark**. After close it was 23 MB (the 200 ms limit left it partly compacted).

Consequences, stated as observed rather than generalised: (1) under a burst the file is **~1.7–17 KB per session**,
one to two orders of magnitude above the ~0.4 KB payload, and super-linear in burst size (MVStore retains old
versions for 45 s and copies-on-write every page it touches); (2) the **cleanup DELETE itself roughly triples the
file** at that moment, so the disk peak arrives *after* the attack, when the cron runs; (3) an idle, open database
does not give the space back. Numbers depend on hardware, heap and write pattern; they are an order-of-magnitude
check, not a sizing figure.

### C.2 File size from SQL

**CONFIRMED in source; NUANCED — not documented.**
`SELECT SETTING_VALUE FROM INFORMATION_SCHEMA.SETTINGS WHERE SETTING_NAME = 'info.FILE_SIZE'` returns the MVStore
file size in bytes. Chain: `InformationSchemaTable.settings` → `database.populateInfo` (`H2` +
`table/InformationSchemaTable.java` line 2810) → `Database.populateInfo` → `getStore().getMvStore().populateInfo`
(`engine/Database.java` lines 529–539) → `FileStore.populateInfo` emits `info.FILE_SIZE` = `size()`
(`mvstore/FileStore.java` line 1714), alongside `info.FILL_RATE`, `info.CHUNKS_FILL_RATE`, `info.CHUNK_COUNT`,
`info.PAGE_COUNT_LIVE` (lines 1706–1722). The measurement above read it and it matched `File.length()` at every
step. The H2 docs only document the analogous `info.CACHE_MAX_SIZE` (Features › Cache Settings), so
`info.FILE_SIZE` is an **undocumented** key and may change without notice. `RETENTION_TIME` and `WRITE_DELAY` are
also exposed in the same view (`Database.java` lines 533–534).

### C.3 Max file size / disk quota setting

**CONFIRMED: none.** No `DbSettings` key or `SET` command sets a file-size cap: the full `DbSettings` key list at
the tag has no size limit, and `res/help.csv` has only `SET TRACE_MAX_FILE_SIZE` (the **trace** file). The only
documented limit is the format ceiling: 4 TB at the default page size
([Advanced › Limits and Limitations](https://www.h2database.com/html/advanced.html)), plus the host filesystem's
own limits (e.g. 4 GB on FAT32, mitigable with `split:`). A quota must come from outside H2 (volume size,
filesystem quota).

### C.4 Disk full during a write

**NUANCED: failure is fail-stop in source; the corruption risk is not documented either way.**

- A failed write throws `ERROR_WRITING_FAILED` (`H2` + `mvstore/DataUtils.java` lines 470–482, wrapping the
  `IOException`).
- Chunk serialisation and `storeBuffer` catch any exception and call `mvStore.panic(e)` (`mvstore/FileStore.java`
  lines 1443–1446, 1531–1534). `panic` records the exception; the next unlock sees it and calls
  `closeImmediately()` and rethrows (`mvstore/MVStore.java` lines 491–506). **The store closes; the database is
  unusable until reopened.**
- Durability model: chunks are never updated in place, have footers with checksums, and on open the newest valid
  chunk is located from the header and chunk footers ([MVStore › File Format](https://www.h2database.com/html/mvstore.html)),
  so a torn final chunk should roll back to the previous version. That is **inference from the design**, not a
  documented guarantee. The H2 [FAQ › Is it Reliable?](https://www.h2database.com/html/faq.html) says some users
  could not open a database after a power failure and points to backups or the Recover tool;
  [Advanced › Durability](https://www.h2database.com/html/advanced.html) says about one second of committed
  transactions may be lost by default (`WRITE_DELAY`). No H2 page documents disk-full specifically.
- **Consequence.** Disk-full on the H2 volume is an availability failure of the **whole** application (sessions,
  users, audit share the file), with un-flushed commits at risk. This is not verified by a fill-the-disk test.

## §D — Spring Boot 4.1 `DiskSpaceHealthIndicator`

### D.1 Properties: single path only

**CONFIRMED: one path.** `@ConfigurationProperties("management.health.diskspace")` with `File path = new File(".")`
and `DataSize threshold = 10MB` (`SB` +
`module/spring-boot-health/src/main/java/org/springframework/boot/health/autoconfigure/application/DiskSpaceHealthIndicatorProperties.java`
lines 33–44; `setThreshold` rejects negatives, line 59). The 4.1 appendix lists exactly
`management.health.diskspace.enabled` (true), `.path`, `.threshold` (10MB)
([application properties](https://docs.spring.io/spring-boot/4.1/appendix/application-properties/index.html)).
No list/map form exists. The default `.` is the **working directory**, which is not necessarily the H2 volume.

### D.2 A second indicator for another path

**CONFIRMED: define a bean with a different name; the default stays.**

- The auto-configured bean is `@ConditionalOnMissingBean(name = "diskSpaceHealthIndicator")`, i.e. keyed on
  **bean name**, not type (`SB` +
  `module/spring-boot-health/src/main/java/org/springframework/boot/health/autoconfigure/application/DiskSpaceHealthContributorAutoConfiguration.java`
  lines 44–48; whole class gated by `@ConditionalOnEnabledHealthIndicator("diskspace")`, line 40). A user bean
  named e.g. `h2DataDiskSpaceHealthIndicator` does **not** back it off; a user bean named `diskSpaceHealthIndicator`
  replaces it.
- The registry takes **every** `HealthContributor` bean (`Map<String, HealthContributor>`) and names each via
  `HealthContributorNameGenerator.withoutStandardSuffixes()` unless one is supplied
  (`SB` + `module/spring-boot-health/src/main/java/org/springframework/boot/health/autoconfigure/registry/HealthContributorRegistryAutoConfiguration.java`
  lines 52–60), which strips a case-insensitive `healthindicator` / `healthcontributor` suffix
  (`…/registry/HealthContributorNameGenerator.java` lines 77–95).
- So both appear: `diskSpace` (default, `.`) and `h2DataDiskSpace` (custom), each subject to the `/health`
  aggregate status. Construction: `new DiskSpaceHealthIndicator(File path, DataSize threshold)`
  (`DiskSpaceHealthIndicator.java` line 53).

### D.3 What it measures

**CONFIRMED: usable space on the filesystem holding the path, not a file's size.** `doHealthCheck` compares
`this.path.getUsableSpace()` with the threshold, reports `DOWN` below it (with a WARN log), and adds details
`total` (`getTotalSpace()`), `free`, `threshold`, `path`, `exists` (`SB` +
`module/spring-boot-health/src/main/java/org/springframework/boot/health/application/DiskSpaceHealthIndicator.java`
lines 59–76). `File.getUsableSpace()` is a per-partition figure, so pointing it at the H2 file or its directory
measures **free space on that mount**; it says nothing about how large `*.mv.db` is — that needs §C.2.

## §E — Session counts and metrics

**CONFIRMED on all three.**

- **No count API.** `SessionRepository` has no count method (§A.4); `JdbcIndexedSessionRepository`'s public
  surface adds only setters, `findByIndexNameAndIndexValue` and `cleanUpExpiredSessions` (which logs, at DEBUG,
  the number deleted; `SS-JDBC` lines 647–655). Counting requires a query against `SPRING_SESSION`.
- **No Micrometer metrics in Spring Session.** No path in the `4.1.1` source tree matches `micrometer`, `metric`
  or `MeterBinder` (full, non-truncated GitHub tree listing). The Boot `spring-boot-session*` modules at `v4.1.1`
  contain no metrics classes either.
- **Tomcat session metrics do not see Spring Session sessions.** Boot binds Micrometer's `TomcatMetrics` to the
  Tomcat context's `Manager` (`SB` +
  `module/spring-boot-tomcat/src/main/java/org/springframework/boot/tomcat/metrics/TomcatMetricsBinder.java`
  lines 60–80); `tomcat.sessions.active.current` is `Manager::getActiveSessions`
  (`MM` + `micrometer-core/src/main/java/io/micrometer/core/instrument/binder/tomcat/TomcatMetrics.java` line 117).
  `SessionRepositoryFilter`'s request wrapper overrides `getSession` and creates sessions via
  `sessionRepository.createSession()`, never delegating to the container (`SessionRepositoryFilter.java`
  lines 282–325). The filter runs at `Integer.MIN_VALUE + 50` for `ASYNC, ERROR, REQUEST`
  (line 103; `SB` + `module/spring-boot-session/src/main/java/org/springframework/boot/session/autoconfigure/SessionProperties.java`
  lines 86–94). So the Tomcat gauges stay at ~0 unless something ahead of that filter, or outside those dispatch
  types, calls `getSession` — **inferred from source, not run**.

## §F — Added in the first grilling round (evidence behind the Q1 and Q2 premise corrections)

Added because the round-1 answers rested on these and the verification rule requires them here first. Line
numbers are against the raw files at the tags above.

### F.1 Every unsafe request with no session creates one, on any path, before authorization

**CONFIRMED.**

- `CsrfFilter.doFilterInternal` calls `tokenRepository.loadDeferredToken` unconditionally (`SEC` +
  `web/src/main/java/org/springframework/security/web/csrf/CsrfFilter.java` line 110). It returns early only for
  `GET, HEAD, TRACE, OPTIONS` (lines 113, 209). Otherwise it calls `deferredCsrfToken.get()` (line 121) and
  branches on `isGenerated()` for missing vs invalid (line 127).
- `RepositoryDeferredCsrfToken.init()` calls `loadToken`. If that returns null, it calls `generateToken` **and then
  `saveToken`** (`…/csrf/RepositoryDeferredCsrfToken.java` lines 66–70).
- `HttpSessionCsrfTokenRepository.saveToken` with a non-null token calls `request.getSession()`, which is
  `getSession(true)` (`…/csrf/HttpSessionCsrfTokenRepository.java` lines 58–60). With a null token it uses
  `getSession(false)` and never creates a session (lines 52–56).
- **So a POST/PUT/PATCH/DELETE with no session writes 1 + 1 rows (§A.1) and then gets its 403.** It does this on
  any path, because `CsrfFilter` runs before authorization. Ticket 26 (`26:142`) cited the `get()` call as a lookup
  cost. It did not see the write.

### F.2 A wrapping repository cannot tell the filter's save from the controller's, and must not delegate `loadDeferredToken`

**CONFIRMED.**

- `loadDeferredToken` is an **interface default** returning `new RepositoryDeferredCsrfToken(this, request, response)`
  (`…/csrf/CsrfTokenRepository.java` lines 72–73). So the lazy save from `CsrfFilter` goes through whatever `this`
  is, and both the filter and the bootstrap controller reach the same `saveToken`.
- `HttpSessionCsrfTokenRepository` is `final` (line 35) and does **not** override `loadDeferredToken`. A wrapper
  therefore has to be a delegating wrapper rather than a subclass. **It must inherit the interface default rather
  than forward `loadDeferredToken` to the delegate.** Forwarding it would bind the deferred token to the inner
  repository, and the wrapper's `saveToken` would never be called.

### F.3 Any request carrying a valid session cookie refreshes `LAST_ACCESS_TIME`, and the refresh is persisted

**CONFIRMED.**

- `SessionRepositoryRequestWrapper.getSession(boolean)` calls `setLastAccessedTime(Instant.now())` on a resolved
  requested session (`SS` + `…/web/http/SessionRepositoryFilter.java` lines 287–295).
  `isRequestedSessionIdValid()` does the same (lines 266–274).
- `JdbcSession.setLastAccessedTime` sets `changed = true` (`SS-JDBC` lines 856–859). At commit, the non-new
  branch then writes `LAST_ACCESS_TIME` and a recomputed `EXPIRY_TIME` (lines 176, 921–922).
- Ticket 26 §1 established that `SessionManagementFilter` calls `getSession(false)` on every request. So a GET on
  any path with a live cookie **extends that anonymous row by a full idle window**. The miss budget does not meter
  it, because the lookup succeeds.

### F.4 A negative `maxInactiveInterval` is immortal, and the repository then cannot delete the row

**CONFIRMED; the second half is new.**

- `MapSession.isExpired` returns `false` for a negative interval (`SS` +
  `spring-session-core/src/main/java/org/springframework/session/MapSession.java` lines 184–188).
- `JdbcSession.getExpiryTime` returns `Long.MAX_VALUE` for a negative interval (`SS-JDBC` lines 776–780), so the
  cleanup predicate `EXPIRY_TIME < ?` never matches.
- **`DELETE_SESSION_QUERY` carries `AND MAX_INACTIVE_INTERVAL >= 0`** (`SS-JDBC` lines 193–197). So
  `deleteById`, which is what invalidation uses, also skips a row once a negative interval has been saved.
- **Consequence for Q2's pin.** A remaining lifetime of zero or less must lead to `invalidate()`, and must never
  reach `setMaxInactiveInterval`. If a negative value is ever committed, the row cannot be removed by any path the
  application has. `Duration.ZERO` is safe (`isExpired` is true for it, line 188), but there is no reason to write it.

### F.5 A Micrometer gauge is evaluated on the push registry's own thread at publish time

**CONFIRMED.**

- `DefaultGauge.value()` applies the value function to a **weakly referenced** state object each time it is read.
  It returns `NaN` if that object has been garbage-collected, or if the function throws; the exception is logged,
  not propagated (`MM` + `micrometer-core/src/main/java/io/micrometer/core/instrument/internal/DefaultGauge.java`
  lines 38, 44, 49–59).
- `PushMeterRegistry.start` creates its own single-thread `ScheduledExecutorService` and runs publish at a fixed
  rate (`MM` + `micrometer-core/src/main/java/io/micrometer/core/instrument/push/PushMeterRegistry.java`
  lines 104–114). `OtlpMeterRegistry.publish` iterates `getMeters()` (`MM` +
  `implementations/micrometer-registry-otlp/src/main/java/io/micrometer/registry/otlp/OtlpMeterRegistry.java`
  lines 173, 308–314).
- **Consequences.** (1) A gauge needs no scheduler of the application's own, which is the second of ticket 21's
  `21:401-402` reasons shown false (§A.3 was the first). (2) A `COUNT` gauge's state object must be a strongly held
  bean (e.g. the `DataSource` or a repository), or the gauge quietly reads `NaN`. (3) After an H2 panic (§C.4) the
  `COUNT` gauge reads `NaN` and logs. A filesystem size read (`Files.size`) keeps working.

### F.6 An unfiltered `COUNT(*)` on H2 takes the quick-aggregate path; a filtered one does not

**NUANCED.** Added in the second grilling round.

- `Select` sets `isQuickAggregateQuery` only when every expression passes the `OPTIMIZABLE_AGGREGATE` visitor
  against the table (`H2` + `command/query/Select.java` line 1219). For `COUNT(*)` that visitor asks
  `table.canGetRowCount(session)` (`expression/aggregate/Aggregate.java` lines 1323–1331). The value then comes
  from `table.getRowCount(session)` (line 524), which `MVTable` delegates to the primary index
  (`mvstore/db/MVTable.java` lines 618–619, 803).
- So `SELECT COUNT(*) FROM SPRING_SESSION` is a row-count read. `… WHERE PRINCIPAL_NAME IS NULL` has a filter,
  so it is an index range scan over IX3.
- **Not verified:** what the primary index's `getRowCount` costs under concurrent uncommitted writes. MVCC may have
  to reconcile pending changes. So "O(1)" is the idle-case claim only.

---

## Summary table

| # | Question | Answer | Status |
|---|---|---|---|
| A.1 | Rows on new session | 1 `SPRING_SESSION` + 1 per attribute, one tx at commit; zero-attribute session persisted iff `getSession(true)` was called | CONFIRMED / NUANCED |
| A.2 | H2 schema | As table; IX1 unique `SESSION_ID`, IX2 `EXPIRY_TIME`, IX3 `PRINCIPAL_NAME`; FK cascade; H2 script lacks the Javadoc's `ATTRIBUTES_IX1` | CONFIRMED |
| A.3 | Cleanup | `0 * * * * *`, `spring.session.jdbc.cleanup-cron`, `EXPIRY_TIME < now`; **own scheduler thread, no `@EnableScheduling`** | CONFIRMED (ticket 21 premise wrong) |
| A.4 | Global session cap | None | CONFIRMED |
| A.5 | `changeSessionId` | UPDATE `SESSION_ID` on same `PRIMARY_ID` row | CONFIRMED |
| B | CSRF attribute size | 205 bytes measured (raw `DefaultCsrfToken`, not the XOR-masked one) | CONFIRMED |
| C.1 | H2 file shrink | Reused while open; truncates on some writes and on close; not while idle; `SHUTDOWN COMPACT` exists; retention 45 s, write delay 500 ms, close-compaction 200 ms; bulk DELETE inflates file ~3× | NUANCED |
| C.2 | Size from SQL | `INFORMATION_SCHEMA.SETTINGS` `info.FILE_SIZE` — works, undocumented | NUANCED |
| C.3 | Max size / quota | None (only 4 TB format limit) | CONFIRMED |
| C.4 | Disk full | Write fails → `panic` → store closed; corruption risk undocumented | NUANCED |
| D.1 | Diskspace properties | Single `path` + `threshold` (10MB), default `.` | CONFIRMED |
| D.2 | Second indicator | Differently named bean; default is `@ConditionalOnMissingBean(name=…)`; both reported | CONFIRMED |
| D.3 | What it measures | `File.getUsableSpace()` of the mount | CONFIRMED |
| E | Session counts/metrics | No count API, no Spring Session metrics; Tomcat session gauges don't see them | CONFIRMED (Tomcat part source-inferred) |
| F.1 | Unsafe request, no session | `CsrfFilter` → `init()` → `generateToken` + `saveToken` → `getSession(true)`: a row is written before the 403, on any path | CONFIRMED |
| F.2 | Wrapper mechanics | `loadDeferredToken` is an interface default bound to `this`; delegate is `final`; wrapper must not forward it | CONFIRMED |
| F.3 | Keepalive | Any request with a live cookie persists a new `LAST_ACCESS_TIME`/`EXPIRY_TIME` | CONFIRMED |
| F.4 | Negative interval | Never expires, is skipped by cleanup **and** by `deleteById` (`MAX_INACTIVE_INTERVAL >= 0`) | CONFIRMED |
| F.5 | Gauge evaluation | Evaluated at publish on the push registry's own thread; weak reference; `NaN` on failure | CONFIRMED |
