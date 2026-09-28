@nfr @security
Feature: Non-functional and security requirements
  Cross-cutting constraints from the PRD that apply to every story.

  Rule: Role enforcement - a USER calling any /api/admin/** endpoint gets 403

    Scenario Outline: A USER is forbidden from <method> <path>
      Given a registered user "alice"
      And a registered user "target"
      And "alice" is logged in
      When "alice" sends <method> "<path>" with:
        """
        <body>
        """
      Then the response status is 403
      And the account "target" exists with role "USER" and enabled status "TRUE"

      Examples:
        | method | path                               | body                  |
        | GET    | /api/admin/users                   | {}                    |
        | PATCH  | /api/admin/users/{id:target}/status | { "enabled": false } |
        | PATCH  | /api/admin/users/{id:target}/role   | { "role": "ADMIN" }  |
        | DELETE | /api/admin/users/{id:target}        | {}                   |

    Scenario: An anonymous client calling an admin endpoint is unauthorized
      When an anonymous client sends GET "/api/admin/users"
      Then the response status is 401

    Scenario: A client-supplied role on registration is ignored
      Given a visitor has chosen new account details "mallory"
      When the visitor registers "mallory" via the API with the extra field "role" set to "ADMIN"
      Then the account "mallory" exists with role "USER" and enabled status "TRUE"

  Rule: Session security - HttpOnly + SameSite cookie, fixation protection

    Scenario: The session cookie is HttpOnly and SameSite
      Given a registered user "alice"
      When "alice" logs in with the correct password
      Then the response sets an HttpOnly "JSESSIONID" cookie
      And the "JSESSIONID" cookie set by the response has a SameSite attribute

    # Secure is disabled in the dev profile because local dev runs over plain
    # HTTP (documented deployment assumption in the PRD); application.yml keeps
    # secure: true for every other profile.
    @skip
    Scenario: The session cookie is Secure outside local development
      Given a registered user "alice"
      When "alice" logs in with the correct password
      Then the "JSESSIONID" cookie set by the response is Secure

    Scenario: Logging in issues a new session id (session fixation protection)
      Given a registered user "alice"
      And "alice" already holds an anonymous session cookie
      When "alice" logs in with the correct password
      Then the response status is 200
      And the session id of "alice" differs from the anonymous one
      And the anonymous session cookie cannot access "/api/hello"

  Rule: CSRF - every state-changing endpoint requires the CSRF token

    Scenario Outline: <method> <path> without a CSRF token is rejected
      Given a registered admin "root"
      And a registered user "target"
      And "root" is logged in
      When "root" sends <method> "<path>" without a CSRF token with:
        """
        <body>
        """
      Then the response status is 403
      And the account "target" exists with role "USER" and enabled status "TRUE"

      Examples:
        | method | path                                | body                                                                                   |
        | POST   | /api/register                       | { "username": "e2e_csrf_{random}", "email": "csrf{random}@example.test", "password": "Csrf-Password-2026" } |
        | POST   | /api/login                          | { "username": "{username:target}", "password": "{password:target}" }                  |
        | POST   | /api/logout                         | {}                                                                                     |
        | POST   | /api/password-reset/request         | { "email": "{email:target}" }                                                          |
        | POST   | /api/password-reset/confirm         | { "token": "abc.def", "newPassword": "Csrf-Password-2026" }                            |
        | PATCH  | /api/admin/users/{id:target}/status | { "enabled": false }                                                                   |
        | PATCH  | /api/admin/users/{id:target}/role   | { "role": "ADMIN" }                                                                    |
        | DELETE | /api/admin/users/{id:target}        | {}                                                                                     |

    Scenario: Logout without a CSRF token leaves the session intact
      Given a registered user "alice"
      And "alice" is logged in
      When "alice" sends POST "/api/logout" without a CSRF token
      Then the response status is 403
      And "alice" can access "/api/hello"

  Rule: CORS - explicit frontend allow-list with credentials

    Scenario: A preflight from the frontend origin is allowed with credentials
      When a CORS preflight for POST "/api/login" is sent from the frontend origin
      Then the response allows the frontend origin with credentials

    Scenario: A preflight from an unknown origin is not allowed
      When a CORS preflight for POST "/api/login" is sent from origin "https://evil.example"
      Then the response does not allow origin "https://evil.example"

  Rule: Audit logging - structured events with actor and target, never passwords

    Scenario: Login success, login failure and lockout are audit-logged without passwords
      Given a registered user "alice"
      When someone fails to log in as "alice" 5 times
      And the lockout of "alice" has expired
      And "alice" logs in with the correct password
      Then the backend log records "Login failed: bad credentials username={username:alice}"
      And the backend log records "Account locked username={username:alice}"
      And the backend log records "Login succeeded username={username:alice}"
      And the plaintext password of "alice" does not appear in the backend log
      And the backend log is structured JSON

    Scenario: Password reset request and completion are audit-logged without secrets
      Given a registered user "alice"
      And "alice" has requested a password reset and received the reset link
      When an anonymous client confirms the reset of "alice" with the new password "Audited-New-Password-2026"
      Then the response status is 200
      And the backend log records "Password reset requested username={username:alice}"
      And the backend log records "Password reset completed username={username:alice}"
      And the text "Audited-New-Password-2026" does not appear in the backend log
      And the latest reset token secret of "alice" does not appear in the backend log

    Scenario: Admin actions are audit-logged with actor and target
      Given a registered admin "root"
      And a registered user "alice"
      And a registered user "bob"
      And "root" is logged in
      When "root" sends PATCH "/api/admin/users/{id:alice}/status" with:
        """
        { "enabled": false }
        """
      And "root" sends PATCH "/api/admin/users/{id:alice}/status" with:
        """
        { "enabled": true }
        """
      And "root" sends PATCH "/api/admin/users/{id:alice}/role" with:
        """
        { "role": "ADMIN" }
        """
      And the id of "bob" is known
      And "root" sends DELETE "/api/admin/users/{id:bob}"
      Then the backend log records "Admin action: disable actor={username:root} target={username:alice}"
      And the backend log records "Admin action: enable actor={username:root} target={username:alice}"
      And the backend log records "Admin action: role-change actor={username:root} target={username:alice} newRole=ADMIN"
      And the backend log records "Admin action: delete actor={username:root} target={username:bob}"
