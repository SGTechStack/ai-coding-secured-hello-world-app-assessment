# 02: Frontend skeleton and credentialed API client

**What to build:** The first genuinely demoable slice. A React app on its own origin loads in a
browser, calls the backend's unauthenticated liveness endpoint **cross-origin with credentials**,
and renders the result. This proves the CORS-plus-credentials-plus-CSRF triangle works end to
end in a real browser, which is exactly the thing that silently breaks late and expensively if
it isn't nailed down first.

Establishes the shared API client that every later frontend ticket uses, so no ticket has to
reinvent credential or CSRF handling.

**Blocked by:** 01.

**Status:** ready-for-agent

**IM8 controls:** `as-1` Input Validation; `as-3` Output Sanitisation; `as-6` Password Salting
and Hashing (frontend obligations); `dp-3` Data in Transit Encryption. *ASVS: V14
Configuration, V3.4 Cookie-based Session Management, V5 Validation and Encoding.*

- [ ] React app runs on its own origin, separate from the backend
- [ ] The stack is **TypeScript**: every source file is `.ts` or `.tsx` and no `.js`/`.jsx`
      source file is committed (build config and generated output aside). This is not a taste
      preference — the downstream `im8-review` code audit greps TypeScript sources, so a
      JavaScript codebase is silently under-reported rather than flagged
- [ ] A single shared API client module is the only place network calls originate; it sends
      credentials on every request so the session cookie is included cross-origin
- [ ] The API client attaches the CSRF token to every state-changing request automatically,
      so callers cannot forget it
- [ ] The API client surfaces backend errors as typed, renderable outcomes rather than throwing
      raw responses at components
- [ ] The landing page calls the liveness endpoint and renders whether the backend is reachable
- [ ] Every user-controlled or backend-supplied value renders through React's default JSX
      escaping. `dangerouslySetInnerHTML` appears **nowhere** in the codebase, and the ban is
      enforced by lint rather than left to review
- [ ] Form inputs are validated client-side against the **same rules the server enforces**,
      expressed in a runtime schema validator (Zod, or yup/joi/valibot) rather than ad-hoc `if`
      checks, so the rules are one declared object instead of drifting logic. Client validation
      is a usability affordance only — it never replaces the server-side check, which stays
      authoritative because anything in the browser is attacker-controlled
- [ ] The password is handled as transient input only: it is **never hashed client-side** (the
      server salts and hashes; a client-side hash just becomes the password), never written to
      `localStorage` or `sessionStorage`, and never placed in a URL path or query string where
      it lands in browser history and access logs
- [ ] Password fields do not set `autocomplete="off"` — it adds no security and fights password
      managers, pushing users toward weaker, reused passwords
- [ ] No secret, credential, or backend origin is hardcoded — the backend origin comes from
      build-time configuration
- [ ] The API base URL is read from configuration with no `http://` literal in the source, so a
      deployment can point at HTTPS without a code change
- [ ] Test: the app renders a success state when the backend responds, and a handled error
      state when it does not
- [ ] Manual verification recorded: the cross-origin call succeeds in a real browser with
      cookies enabled, not just in a test harness
