# 03: Sign in, greeting, sign out

**What to build:** An Account holder signs in on the SPA with username and password, sees "Hello, <username>", and signs out. Sign-in is a CSRF-protected JSON endpoint returning one generic failure for unknown username, wrong password and disabled Account. A fresh session id is issued at sign-in. Sign-out ends the server session so a replayed cookie is rejected. The template's `{noop}password` login and the auto-creation of unknown usernames are removed.

**Blocked by:** 02 Seed the initial admin

**Status:** ready-for-agent

- [ ] Correct credentials create a session, regenerate the session id and reset the failed-attempt counter
- [ ] Unknown username, wrong password and disabled Account return an identical generic error
- [ ] Sign-in and sign-out require the CSRF token; the template's CSRF exemption for both is removed
- [ ] `GET /api/hello` returns "Hello, <username>" when authenticated and 401 otherwise
- [ ] A session cookie replayed after sign-out is rejected as unauthenticated
- [ ] The local stand-in login and auto-provisioning of unknown usernames no longer exist
- [ ] Audit lines for sign-in success and failure, with no password logged
- [ ] The SPA has a sign-in page and shows the greeting; generated API client and OpenAPI docs are regenerated as documented
- [ ] Integration tests cover success, wrong password, unknown username, disabled Account, CSRF required and logout replay; frontend tests cover the sign-in form
- [ ] Backend and frontend verify pass
