# 31: Dev-only demo accounts

**What to build:** For demos, a `dev` start seeds two ready-to-use accounts, and the sign-in page lists them. Nothing
here exists or works outside the `dev` profile.

- **Seeding** (dev, web only, before `AdminBootstrap`, idempotent): `demo-user`, an activated `USER`, and `demo-admin`,
  an activated `ADMIN` pre-enrolled in TOTP (secret sealed as a real enrolment is), both with committed passwords and
  no forced change. The runner mode never seeds.
- **`GET /api/dev/demo-accounts`** (dev only): each demo account's username, role and password (or a changed flag),
  and the admin's current code and seconds remaining, computed on the server with `TotpWindow` and the `Clock`.
- **SPA:** a "Demo accounts" panel on the sign-in page, shown only on a 200 with accounts, with copy and fill-in
  buttons and a code that refreshes as its step ends. No demo value in the bundle.
- **Refused outside dev:** the demo passwords as `app.admin.password`, any `app.dev.demo-accounts` property, the dev
  whitelist, and a database still holding an activated demo account.

**Blocked by:** 16, 17, 18, 23, 28

**Status:** done

- [x] Under dev both accounts are seeded once, ready to sign in; the admin is enrolled and authenticable; the
      bootstrap then seeds none; a restart changes nothing.
- [x] Outside dev nothing is seeded, the route answers as an unknown route does, and the demo values, the dev
      whitelist and a carried-over demo account are refused at startup.
- [x] The panel's code is the one `TotpWindow` accepts; a changed password is flagged and hidden.
- [x] The SPA panel renders only on a 200 with accounts; copy, fill-in and refresh work; the built bundle holds no demo
      value.
- [x] Playwright: the demo user signs in from the panel, and the demo admin reaches `/admin/users` with the panel's
      code.
- [x] README, backend README and UAT guide updated; the changed UAT steps were checked against the running app.
