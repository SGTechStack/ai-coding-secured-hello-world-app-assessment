# 18: Admin user list

**What to build:** An admin opens the admin page and sees who has access to the system: each
user's username, email, role, enabled status and created-at date. **Never password hashes** —
the response shape is itself a security control, because an endpoint that serialises the whole
user entity will leak the hash the moment someone adds a field.

Covers PRD Story 8's positive criterion.

**Blocked by:** 17.

**Status:** ready-for-agent

**IM8 controls:** `ac-1` Principle of Least Privilege; `as-7` Access Control Check
Enforcement; `ac-4` Access Review; `as-6` Password Salting and Hashing; `dp-8` Data
Classification Disclosure; `lm-4` Audit Logging. *ASVS: V4.1 General Access Control, V8 Data
Protection, V13 API.*

- [ ] An authenticated admin listing users gets, per user: username, email, role, enabled status,
      created-at date
- [ ] The response **never** includes the password hash, and never includes the reset-token
      material either
- [ ] The response is built from an explicit response shape that lists the fields to include,
      rather than serialising the persistence entity — so adding a sensitive column later cannot
      silently expose it
- [ ] The list is paginated or otherwise bounded, so the endpoint does not become a
      denial-of-service vector or an unbounded data dump as the user count grows
- [ ] Email addresses are recognised as personal data and their presence in this response is
      justified by the admin's need to review access
- [ ] A privileged-read audit event records which admin listed the users
- [ ] The frontend renders the list on an admin page reachable only from admin navigation
- [ ] Test: an admin receives the specified fields for each user
- [ ] Test: the serialised response contains no password hash under any field name — asserted
      against the raw response body, not against a mapped object
- [ ] Test: a `USER` still receives 403 here (ticket 17's boundary has not regressed)
