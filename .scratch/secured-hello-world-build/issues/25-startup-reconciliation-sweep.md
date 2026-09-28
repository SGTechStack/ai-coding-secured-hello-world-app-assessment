# 25: Startup reconciliation sweep

**What to build:** Session invalidation is dispatched after commit, so a crash between the commit and the dispatch can leave a session alive. At startup, `SessionTerminationService` sweeps and ends every session whose principal is disabled, deleted, under an in-force lock, capped, or under a tier-2 factor disable (ADR-039; R-SES-012).

**Blocked by:** 12, 19, 22

**Status:** ready-for-agent

- [ ] In the restart harness, a session left alive after each trigger (disable, delete, in-force lock, cap, tier-2 disable) is gone after the second boot (T-SES-022; T-SES-036).
- [ ] Sessions of unaffected users survive the sweep.
- [ ] The sweep emits an audit row with its counts.
