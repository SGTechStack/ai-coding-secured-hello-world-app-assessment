Feature: Account Registration
  As a visitor,
  I want to register an account with a username, email, and password,
  So that I can log in and access the protected app.

  # Password policy: 12 characters minimum, 72 bytes maximum (BCrypt ignores input beyond 72 bytes,
  # so longer passwords are rejected instead of silently truncated).
  # Usernames and emails are unique case-insensitively and stored lowercase.

  Background:
    Given the visitor holds a CSRF token from "GET /api/csrf"
    And an account exists with username "johndoe" and email "john@example.com"

  @story1-ac1 @api
  Scenario: Registering with a unique username, unique email and strong password creates a USER account
    When the visitor sends "POST /api/auth/register" with:
      | field    | value                 |
      | username | janedoe               |
      | email    | jane@example.com      |
      | password | correct-horse-battery |
    Then the response status is 201
    And an account "janedoe" exists with email "jane@example.com", role "USER" and enabled "true"
    And the account's "created_at" is set to the time of registration
    And the account's "password_hash" is a BCrypt hash that matches "correct-horse-battery"
    And the response body contains neither the password nor its hash
    And the visitor is not logged in

  @story1-ac2 @api
  Scenario Outline: Registering with an already-registered username or email is rejected
    When the visitor sends "POST /api/auth/register" with username "<username>", email "<email>" and password "correct-horse-battery"
    Then the response status is 409
    And the response body is '{"message": "<message>"}'
    And no new account is created

    Examples:
      | username | email            | message                     |
      | johndoe  | new@example.com  | Username is already taken   |
      | JohnDoe  | new@example.com  | Username is already taken   |
      | newuser  | john@example.com | Email is already registered |
      | newuser  | JOHN@Example.com | Email is already registered |

  @story1-ac3 @api
  Scenario Outline: Registering with a password that fails the strength policy is rejected
    When the visitor sends "POST /api/auth/register" with username "janedoe", email "jane@example.com" and <password>
    Then the response status is 400
    And the response body is '{"message": "<message>"}'
    And no account is created

    Examples:
      | password                   | message                                  |
      | an empty password          | Password is required                     |
      | an 11-character password   | Password must be at least 12 characters  |
      | a 73-byte password         | Password must be at most 72 bytes        |

  @story1-ac4 @api
  Scenario Outline: Passwords at the policy boundaries are accepted
    When the visitor sends "POST /api/auth/register" with username "janedoe", email "jane@example.com" and <password>
    Then the response status is 201

    Examples:
      | password                 |
      | a 12-character password  |
      | a 72-byte password       |

  @story1-ac5 @api
  Scenario Outline: Registering with a malformed username or email is rejected
    When the visitor sends "POST /api/auth/register" with username "<username>", email "<email>" and password "correct-horse-battery"
    Then the response status is 400
    And the response body is '{"message": "<message>"}'
    And no account is created

    Examples:
      | username       | email            | message                 |
      |                | jane@example.com | Username is required    |
      | jane doe       | jane@example.com | Username is invalid     |
      | <script>       | jane@example.com | Username is invalid     |
      | janedoe        |                  | Email is required       |
      | janedoe        | not-an-email     | Email is invalid        |

  @story1-ac6 @api
  Scenario: Client-supplied role or account state is ignored on registration
    When the visitor sends "POST /api/auth/register" with:
      | field                 | value                 |
      | username              | janedoe               |
      | email                 | jane@example.com      |
      | password              | correct-horse-battery |
      | role                  | ADMIN                 |
      | enabled               | false                 |
      | failed_login_attempts | -99                   |
    Then the response status is 201
    And the account "janedoe" has role "USER", enabled "true" and failed_login_attempts 0

  @story1-ac7 @api
  Scenario: The plaintext password is never logged or stored
    When the visitor registers "janedoe" with password "correct-horse-battery"
    Then no log line emitted while handling the request contains "correct-horse-battery"
    And no database column holds the value "correct-horse-battery"

  @story1-ac8 @ui
  Scenario: Visitor registers through the registration page and is sent to log in
    Given the visitor is on the "/login" page
    When the visitor follows the "Create an account" link
    Then the visitor is on the "/register" page
    When the visitor fills in username "janedoe", email "jane@example.com" and password "correct-horse-battery"
    And clicks the "Create account" button
    Then the visitor is redirected to "/login"
    And the message "Account created. Please log in." is displayed

  @story1-ac9 @ui
  Scenario: Registration errors from the server are shown on the registration page
    Given the visitor is on the "/register" page
    When the visitor submits username "johndoe", email "new@example.com" and password "correct-horse-battery"
    Then the error "Username is already taken" is displayed
    And the visitor remains on the "/register" page
    And the form inputs and submit button are re-enabled
