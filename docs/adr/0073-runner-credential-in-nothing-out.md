---
status: accepted
---

# ADR-073: The runner takes a credential in and emits nothing; batch mode mints nothing

The recovery runner (ADR-072) never outputs a secret. For a single account, the operator types the new password on
stdin or at an interactive prompt. The runner sets it through the normal password policy and forces a change at
the user's first login. The batch form invalidates credentials and mints nothing. Printing a one-time token is the
obvious runner output, and it was the earlier design. It was abandoned because the runner's stdout is collected.

## Context

The audit stream is written to stdout in every profile, because stdout is the platform's ingestion path, and the
deployer installs log forwarding over it. `System.out` is the same file descriptor. A collector cannot tell bytes
written by Logback from bytes written by `System.out.println`. And a runner launched as a Kubernetes Job or a
systemd one-shot unit is collected like the application itself. So "print the token to stdout, never through the
logging system" did not keep the credential of last resort out of the log pipeline. It put that credential there,
in every profile, for an account that by construction has no other way back in.

Every channel that emits a secret has its own failure:

- A mode-0600 file relies on filesystem permissions, which the application cannot meaningfully assert on the
  platform that matters.
- A write that refuses unless stdout is a terminal cannot be built durably on Java 21. `System.console()` returning
  null is the only terminal test available there. From JDK 22, `System.console()` returns a `Console` even when the
  streams are redirected (JDK-8308591), so the guard would fail open on a JDK upgrade. The correct API,
  `Console.isTerminal()`, does not exist on Java 21.
- Mail delivery does not exist outside `dev`.

## Decision

- **Single run: credential in.**
  - The operator supplies the new password on stdin or at an interactive prompt, never as an argument (T-RUN-013).
  - The runner sets it through the same `PasswordService` as every other path, with no exception at the call site.
    So the 15-character floor, the breach blocklist and the zxcvbn score gate all apply.
  - It sets the forced-change flag, so the user must change the password at first login.
  - It stamps the credential-issued time, so the lazy 30-day expiry applies to a password that is never changed
    (ADR-046).
  - It prints only a success line, the account UUID, and the procedural identifiers: resolved database path,
    schema version and file modification time.
- **Batch run: mints nothing.** It invalidates the credentials and clears the cap, and recovery then follows the
  normal reset flow. One operator-chosen password shared across hundreds of accounts would be worse than the leak
  this replaces.
- **No secret crosses any stream.** T-AUD-027 runs the runner with a known password and asserts that the string
  appears in none of stdout, stderr or the audit file.
- **Prohibited implementations:**
  - a `--password` argument, or any argument that carries a secret. Command-line arguments are visible to other
    local processes (CWE-214).
  - `process.command_line` or `process.args` on any audit row, which would copy a mistyped secret into the audit
    stream (CWE-532; T-AUD-029).
  - `System.console()` nullness to choose a mode or detect a terminal (T-RUN-012). The mode is chosen by an explicit
    `--non-interactive` flag. Interactive mode may call `readPassword()`, and it refuses when `System.console()`
    returns null.

## Considered options

- **Print a single-use token to stdout.** Rejected for the reason above. It was the earlier design.
- **A mode-0600 file, a terminal-only write, or deferral to mail.** Rejected for the reasons in the context.
- **Credential in, nothing out (chosen).** It removes the question rather than answering it: with no secret on any
  stream, it no longer matters whether the stream is collected.

## Consequences

- **ASVS 6.4.1 (L1) is satisfied.** It governs *system-generated* initial secrets, and this path generates none. Its
  final clause ("must not be permitted to become the long term password") is met by the forced change.
- **ASVS 6.4.6 (L3) is withdrawn on this path only.** 6.4.6 says an administrator can start a reset but must not
  choose the user's password. We adopted it above the L1 target, and it still holds on the in-app admin-create and
  admin-reset paths, which keep issuing tokens (ADR-006). On the runner path the operator chooses the password. That
  protection was already notional there: in the token design, the token printed to the operator's own terminal. The
  deferral register records a scoped withdrawal, not a knowing failure. What actually constrains operator abuse, in
  both designs, is the audit trail plus its alert.
- **The strength gate can refuse the password an operator types during an emergency.** The gate stays. A carve-out
  on "set a password" is exactly the defect the password design exists to prevent, so the runbook documents the
  failure mode instead.
- **Batch recovery outside `dev` degrades to one interactive run per account**, each inside an outage window and
  each needing an out-of-band handover of the password to the user. That recovery time is uncosted and recorded as
  such. Once mail transport exists, both forms converge on the normal reset flow.
- **Known behaviour change on JDK 22 or later.** Interactive mode's null-console refusal stops refusing, so a piped
  secret would be read instead of aborting. The plan/apply digest (ADR-074) bounds this to one execution. The JDK
  upgrade is a rehearsal trigger (ADR-072), and the forward fix is `Console.isTerminal()`.
