---
status: accepted
---

# ADR-074: Runner changes are bound by a plan/apply digest

Every destructive run of the recovery runner (ADR-072), single or batch, has two steps. A dry run prints a SHA-256
digest of the account state it would change. The apply step must pass that digest back as `--confirm=<digest>` and
is refused if the state has changed since. For a single-operator tool, a dry-run digest reads as ceremony. It is
kept because it is the only control that stops a leftover invocation from firing again.

## Context

The runner invalidates credentials, and in batch mode it does so for hundreds of accounts. So an operator error
does as much damage as the input file is large. There is also a specific way the runner can fire unintended:

- A literal runner argument left in a persistent deployment spec re-fires on every restart. This could be a unit
  file's `ExecStart`, a container `args:` entry, or a Compose `command:`.
- Reading the trigger from `ApplicationArguments` instead of the `Environment` closes the environment-variable path
  (ADR-072). It does not close this one, because the spec itself is what persists.

A plain confirmation flag does not help, because a leftover spec would carry the flag as well.

## Decision

- **Dry run is the default.** The destructive form needs an explicit flag, a mandatory reason string, and
  `--confirm=<digest>` copied from its own dry run.
- **Single-run digest:** SHA-256 over a canonical tuple of non-secret state:
  - account UUID;
  - scope;
  - the password-disabled timestamp;
  - the credential-issued timestamp;
  - TOTP row existence and creation time;
  - schema version;
  - the resolved absolute database path.
- **Batch digest:** SHA-256 over the concatenation of the per-account tuples, plus the digest of the input file.
  Hashing only the input file would bind the operator's intent but not the state. Binding the state is the part that
  does the work: if any one account changes between plan and apply, the whole confirm is invalid.
- **`--operator` is deliberately left out of the tuple.** So one person can run the preview and a second person
  can apply it, a crude two-person split of the kind break-glass practice favours. The dry run emits its own audit
  row, so the split is visible, not just possible. The audit row shape is in the spec (REJ-090).

## What the digest guarantees, stated exactly

- **Applying changes the state, so a digest can never match twice.** A leftover invocation that carries a valid
  `--confirm` fires at most once, and is refused on every later boot (T-RUN-011). Where the operator applies by hand
  and the spec is left behind afterwards, it is refused from the first boot onward. This holds for every scope.
- **The application is stopped during the run (ADR-072)**, so the database does not move between plan and apply.
  That makes the digest a stronger interlock than the same pattern would be against a live system.

## Considered options

- **No preview.** Rejected: the batch form's blast radius is its whole input.
- **A confirmation flag, or a typed "yes".** Rejected: a leftover spec carries it too, and a typed prompt is
  unavailable in non-interactive mode.
- **A digest of the input file only.** Rejected: it binds intent, not state.
- **A state-bound digest (chosen).**

## Consequences

- **A stale copy of the database passes.** If both the plan and the apply run against a backup, a volume snapshot or
  a `dev` clone, the digest matches, and so do all the runner's other precondition checks. No application-side check
  can tell a stale copy apart. This residual is graded `procedural`. The resolved absolute path, schema version and
  file modification time are printed on the dry run, on the success line and on both audit rows, and the runbook's
  confirm step compares them against the deployed database.
- The digest is not a secret, and it authorises nothing without shell access to a stopped host. It binds a change to
  the state that was previewed. It does not authenticate the operator.
