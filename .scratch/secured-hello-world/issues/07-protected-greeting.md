# 07: Protected personalised greeting

**What to build:** The payoff the whole app exists to demonstrate: a logged-in user sees
`Hello, <username>` and thereby confirms their authentication actually worked. An unauthenticated
caller gets 401 instead. This is the slice that makes the login chain demoable to someone
watching over your shoulder.

Covers PRD Story 5.

**Blocked by:** 06.

**Status:** ready-for-agent

**IM8 controls:** `as-3` Output Sanitisation; `as-7` Access Control Check Enforcement; `as-11`
Session Management. *ASVS: V4.1 General Access Control, V3.3 Session Termination, V13 API.*

- [ ] An authenticated request to the greeting endpoint returns `Hello, <username>` using the
      username of the **session's** principal, never a username supplied in the request
- [ ] The greeting is stored user-controlled input being reflected back, so it is returned as a
      **JSON string value** — never `text/html` — with `Content-Type: application/json` and
      `X-Content-Type-Options: nosniff`, so a browser has no path to interpreting the reflected
      value as markup
- [ ] The frontend renders the greeting through React's default escaping and never through
      `dangerouslySetInnerHTML`, which is the only way the escaping gets bypassed
- [ ] The username allow-list from ticket 05 is the first half of this defence; the JSON content
      type and escaping here are the second, and neither is assumed to be sufficient alone
- [ ] A request with no session returns 401
- [ ] A request with an invalid, tampered, or expired session returns 401
- [ ] The 401 response body is sanitised — it does not explain *why* the session failed in a way
      that helps an attacker distinguish expired from forged
- [ ] The frontend has a greeting page that renders the response, and a route guard that keeps
      unauthenticated visitors off it and redirects them to login
- [ ] The route guard is a **convenience, not the control** — removing it client-side must not
      grant access to the data, because the backend enforces it
- [ ] Test: an authenticated session receives its own username in the greeting
- [ ] Test: a username containing markup characters — to whatever extent ticket 05's allow-list
      permits any — round-trips as **inert text** and is not interpreted, and the response
      content type is JSON rather than HTML
- [ ] Test: no session returns 401
- [ ] Test: a fabricated session cookie value returns 401
- [ ] Test: user A's session cannot obtain user B's greeting
