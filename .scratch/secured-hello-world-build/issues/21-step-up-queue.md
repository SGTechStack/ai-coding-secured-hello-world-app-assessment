# 21: Step-up queue (SPA)

**What to build:** An admin who is asked to step up mid-task has their refused requests queued and replayed once after one successful code. The queue is client-side and single-flight. The SPA does a CSRF re-bootstrap before replay, because the session id rotates on factor grant.

**Blocked by:** 20

**Status:** ready-for-agent

- [ ] Two concurrent mutations refused with `MISSING_FACTOR` open one challenge.
- [ ] After a correct code both are replayed exactly once, with a fresh CSRF token.
- [ ] Cancelling the challenge rejects the queued requests without replaying them.
- [ ] A Playwright test covers the expired factor → challenge → replayed disable path.
