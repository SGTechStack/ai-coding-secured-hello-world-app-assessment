# 13 — Session policy

Type: grilling
Status: open
Blocked by: 02
Map: [Secured Login App](../map.md)

## Question

What are the session timeout policies and the login/logout behaviours?

Answer `Q9` (session timeout policies, `Questions.md:211`) and `Q11` (login and logout redirect behaviours, `Questions.md:261`) of the standard's question set.

- **Timeouts**: idle timeout, absolute maximum session lifetime, concurrent-session policy (may one user hold several sessions at once — which interacts with Story 7's "invalidate all sessions" and with 02's find-by-principal requirement), and what the client sees when a session expires mid-use.
- **Login/logout behaviour**: `Q11` is framed for server-rendered redirects, but this is a cross-origin SPA — so the answer is status codes and response bodies, not `Location` headers. Settle what login success returns (200 with a body? 204? what does the SPA learn about its own role, which ties to 10's self-read endpoint), and what logout returns.
- Session-fixation protection: the PRD requires it (`prd/assessment-prd.md:117`) — confirm the strategy (new session id on authentication) and that it does not break the CSRF token established in 09.
- Logout must invalidate server-side **and** clear the cookie, and a replayed pre-logout cookie must be rejected (Story 4). Decide explicitly what "cleared" means for a cross-origin cookie.

`Q10` (remember-me) is already **out of scope** — record that it was consciously declined rather than leaving `Q10` blank.

Blocked on 02 (the session store decides what timeout and concurrency controls are available).
