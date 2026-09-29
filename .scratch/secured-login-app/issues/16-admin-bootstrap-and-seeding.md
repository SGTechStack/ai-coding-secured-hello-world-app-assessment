# 16 — Admin bootstrap and seeding

Type: grilling
Status: open
Blocked by: 03
Map: [Secured Login App](../map.md)

## Question

How does the first privileged account come into existence?

Answer `Q3` (initial administrator account bootstrap strategy, `Questions.md:61`) and `Q2` (user migration strategy, `Questions.md:34`) of the standard's question set. `Q2` is likely vacuous for a greenfield app — record that rather than leaving it blank.

PRD Story 12 sets the requirement: if no privileged user exists at startup, seed one from configuration (`app.admin.username`, `app.admin.password`), hashed identically to any other account; if one already exists, do not create a duplicate. Reconcile against the standard's `Q3` strategy options, which may prescribe something different — the standard wins where it does.

Settle:

- The existence check: role-based ("no user holds the privileged role") versus username-based, and what happens if the configured admin exists but has been disabled or tombstoned by 10's rules.
- Where the seeding runs (`ApplicationRunner`, `@PostConstruct`, a migration) and how it behaves under 02's schema-management choice.
- How the bootstrap password is supplied and what happens if it is absent — fail fast, or start without an admin. A default fallback password would be a live credential in version control, so rule that out explicitly.
- Whether first login forces a password change, and whether the seed event is audited (12's event list may need a row).
- The role name the seed assigns, which is whatever 03 decided — the PRD's `ADMIN` string will not survive that ticket unchanged.

Blocked on 03 (the role model decides what is being seeded).
