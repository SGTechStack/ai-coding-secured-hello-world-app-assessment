# 10: Frontend app (registration, login, hello, logout, password reset, admin UI)

**What to build:** A React frontend, on its own origin, that lets a visitor
register and log in, see the protected greeting, log out, request and
complete a password reset, and — for an admin — manage other users' 
enabled status, role, and deletion. All requests include credentials and
correctly carry the CSRF token for mutating calls.

**Blocked by:** 02 (Registration), 03 (Login + session), 05 (Logout +
protected hello), 06 (Password reset), 07 (Admin user management), 09
(Cross-cutting security hardening pass)

**Status:** ready-for-agent

- [ ] React app scaffolded under `frontend/`, running on its own origin
      (e.g. `localhost:3000`)
- [ ] API client configured to send credentials (cookies) on every request
      to the backend origin, and to fetch/attach the CSRF token on every
      mutating request
- [ ] Registration form: submits username/email/password; surfaces
      validation errors (duplicate username/email, weak password) from the
      backend
- [ ] Login form: submits username/password; surfaces the generic error on
      failure (never distinguishes unknown username from wrong password);
      on success, navigates to the protected hello page
- [ ] Protected hello page: calls `GET /api/hello` and displays the
      greeting; redirects to login if the call returns 401
- [ ] Logout control: calls `POST /api/logout` and returns the user to a
      logged-out state (login page)
- [ ] Password reset request form: submits an email, shows the generic
      success message
- [ ] Password reset confirm form: accepts a token (e.g. via a link/query
      param) and a new password, submits to the confirm endpoint, surfaces
      expired/used-token errors
- [ ] Admin user management view (visible/usable only when the logged-in
      user has role `ADMIN`): lists users (username, email, role, enabled,
      created-at), with controls to enable/disable, change role, and
      delete another user; disables/hides self-targeting controls for the
      admin's own row; surfaces 403 gracefully for any non-admin who
      reaches the route directly
- [ ] Manual or automated verification that the full flow works end to end
      against the running backend (register → login → hello → logout;
      admin actions against a second seeded/registered user)
