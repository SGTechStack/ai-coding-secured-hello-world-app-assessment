# 15: Reset token expiry and single-use enforcement

**What to build:** The two ways a reset token must fail. An **expired** token is rejected and the
password stays as it was. A token that has **already been used once** is rejected on every
subsequent presentation — so a reset link sitting in a mailbox, a browser history, or a proxy
log cannot be replayed into a second account takeover weeks later.

Covers PRD Story 7, rejection paths.

**Blocked by:** 14.

**Status:** ready-for-agent

**IM8 controls:** `as-14` Secure Cryptographic Libraries; `as-13` Exposure of Internal System
Details; `as-11` Session Management; `lm-4` Audit Logging; `pm-6` System Documentation. *ASVS:
V2.5 Credential Recovery, V7 Error Handling and Logging, V11 Business Logic.*

- [ ] A token past its expiry is rejected and the password is **not** changed
- [ ] A token already marked used is rejected on every further presentation
- [ ] The single-use check and the mark-as-used write are atomic with respect to each other, so
      two simultaneous submissions of the same token cannot both succeed
- [ ] An unknown or malformed token is rejected the same way an expired one is, so the response
      does not confirm that a given token value ever existed
- [ ] Rejection responses do not distinguish expired from used from never-existed to the client;
      the audit log **may** record the distinction
- [ ] Expired and used tokens are eventually purged so the table does not accumulate
      indefinitely, and the retention choice is documented
- [ ] The frontend renders a clear "this link is no longer valid, request a new one" state that
      routes back to the request form
- [ ] Test: an expired token is rejected and the stored password hash is unchanged
- [ ] Test: a token used successfully once is rejected on its second use
- [ ] Test: two concurrent submissions of the same valid token result in exactly one password
      change
- [ ] Test: expired, used, and nonexistent tokens produce identical client responses
