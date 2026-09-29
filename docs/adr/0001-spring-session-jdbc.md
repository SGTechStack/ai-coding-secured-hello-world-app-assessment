# Server sessions live in JDBC

Password reset and an admin disable or role change must end every session that account already has, not only the browser that made the request. Logout must also make a captured cookie useless. A Tomcat in-memory session can invalidate the current request, but it cannot list every session for one username.

## Options

- **Container `HttpSession` only.** Enough for logout of the current browser. Finding every session for one account would need a second registry, and a restart drops them.
- **Spring Session with Redis.** Can look sessions up by principal, and adds a store this assessment does not run.
- **Spring Session JDBC.** Chosen. `FindByIndexNameSessionRepository.findByPrincipalName` lists the account's sessions on the same H2 datasource the accounts already use. The schema moves with the app if the database later becomes Postgres.

## What that commits us to

`SessionService` writes the principal name onto the session at login and deletes those rows on logout, password reset, disable, and role change. The cookie name is `HELLOSESSION`. It is `HttpOnly` and `SameSite=Lax` for local HTTP. `app.security.secure-cookies` turns on `Secure` for a real HTTPS deployment.

IP throttling stays in memory inside `IpThrottle`. That counter is per process. Sessions are not.
