Feature: Cross-Cutting Security Requirements
  As a security-conscious operator,
  I want every endpoint to enforce the platform's session, CSRF, CORS, transport and audit controls,
  So that the security baseline holds regardless of which feature a request touches.

  # Captures the PRD's Non-Functional & Security Requirements so they are traceable like the stories.
  # The frontend runs on its own origin (dev default http://localhost:5173) and calls the API on
  # http://localhost:8080 with credentials included.

  @story13-ac1 @api
  Scenario Outline: The session cookie carries the required attributes for each profile
    Given the application runs with the "<profile>" profile
    When the user "johndoe" logs in
    Then the "SESSION" cookie has attributes "<attributes>"

    Examples:
      | profile | attributes                     |
      | default | HttpOnly; Secure; SameSite=Lax |
      | dev     | HttpOnly; SameSite=Lax         |

  @story13-ac2 @api
  Scenario: Logging in rotates the session identifier to prevent session fixation
    Given the client obtained a session identifier from "GET /api/csrf" before logging in
    When the client logs in as "johndoe"
    Then the post-login session identifier differs from the pre-login one
    And the pre-login session identifier is not authenticated

  @story13-ac3 @api
  Scenario Outline: State-changing requests without a valid CSRF token are rejected
    Given the client is <auth_state>
    When the client sends "<method> <path>" without an "X-CSRF-TOKEN" header
    Then the response status is 403
    And no server state is changed

    Examples:
      | auth_state          | method | path                                 |
      | not logged in       | POST   | /api/auth/register                   |
      | not logged in       | POST   | /api/auth/login                      |
      | logged in as a USER | POST   | /api/auth/logout                     |
      | not logged in       | POST   | /api/auth/password-reset/request     |
      | not logged in       | POST   | /api/auth/password-reset/confirm     |
      | logged in as ADMIN  | PATCH  | /api/admin/users/{johndoe.id}/status |
      | logged in as ADMIN  | PATCH  | /api/admin/users/{johndoe.id}/role   |
      | logged in as ADMIN  | DELETE | /api/admin/users/{johndoe.id}        |

  @story13-ac4 @api
  Scenario: Cross-origin requests from the allowed frontend origin may carry credentials
    Given the allowed origins are configured as "http://localhost:5173"
    When a CORS preflight "OPTIONS /api/auth/login" arrives with Origin "http://localhost:5173" requesting header "X-CSRF-TOKEN"
    Then the response has "Access-Control-Allow-Origin" set to "http://localhost:5173"
    And the response has "Access-Control-Allow-Credentials" set to "true"
    And the response allows the "X-CSRF-TOKEN" and "Content-Type" request headers

  @story13-ac5 @api
  Scenario: Cross-origin requests from any other origin are refused
    Given the allowed origins are configured as "http://localhost:5173"
    When a CORS preflight "OPTIONS /api/auth/login" arrives with Origin "https://evil.example"
    Then the response status is 403
    And the response has no "Access-Control-Allow-Origin" header

  @story13-ac6 @api
  Scenario Outline: Security-relevant actions emit structured audit log lines without secrets
    When <action>
    Then an audit log line is written with "event.action" "<event>" and "event.outcome" "<outcome>"
    And the line identifies <identity>
    And the line contains no password, password hash, reset token or session identifier

    Examples:
      | action                                    | event                   | outcome | identity                   |
      | "johndoe" logs in successfully            | user-authentication     | success | the user "johndoe"         |
      | a login for "johndoe" fails               | user-authentication     | failure | the user "johndoe"         |
      | "johndoe" is locked out                   | account-lockout         | failure | the user "johndoe"         |
      | "johndoe" requests a password reset       | password-reset-request  | success | the user "johndoe"         |
      | "johndoe" completes a password reset      | password-reset-complete | success | the user "johndoe"         |
      | "admin" changes the role of "johndoe"     | user-role-change        | success | actor "admin", target "johndoe" |
      | "admin" enables "johndoe"                 | user-enable             | success | actor "admin", target "johndoe" |
      | "admin" disables "johndoe"                | user-disable            | success | actor "admin", target "johndoe" |
      | "admin" deletes "johndoe"                 | user-delete             | success | actor "admin", target "johndoe" |

  @story13-ac7 @api
  Scenario: Responses over HTTPS carry HSTS
    Given the request reaches the application over HTTPS via the trusted proxy
    When the client sends "GET /api/csrf"
    Then the response has a "Strict-Transport-Security" header with "max-age=31536000"

  @story13-ac8 @api
  Scenario: Authorization decisions ignore client-supplied role claims
    Given the user "johndoe" with role "USER" is logged in
    When the user sends "GET /api/admin/users" with header "X-User-Role: ADMIN" and query "?role=ADMIN"
    Then the response status is 403
