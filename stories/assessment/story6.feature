Feature: Password Reset Request
  As a user who forgot their password,
  I want to request a password reset via my registered email,
  So that I can regain access without contacting an admin.

  # EmailService is a stub: sendPasswordResetEmail(...) logs the reset link instead of sending mail.
  # Reset tokens are generated from a cryptographically secure random source (at least 256 bits);
  # only a hash of the token is persisted.

  Background:
    Given an account "johndoe" with email "john@example.com"
    And the visitor holds a CSRF token from "GET /api/csrf"
    And the reset token lifetime is 30 minutes

  @story6-ac1 @api
  Scenario Outline: The response is identical whether or not the email is registered
    When the visitor sends "POST /api/auth/password-reset/request" with email "<email>"
    Then the response status is 202
    And the response body is '{"message": "If that email is registered, a password reset link has been sent."}'

    Examples:
      | email                |
      | john@example.com     |
      | JOHN@example.com     |
      | nobody@example.com   |

  @story6-ac2 @api
  Scenario: A registered email gets a single-use hashed token and a reset email
    When the visitor sends "POST /api/auth/password-reset/request" with email "john@example.com"
    Then one password reset token is stored for "johndoe"
    And the stored "token_hash" is a hash of the issued token, not the token itself
    And the plaintext token is not stored in any database column
    And the token's "expires_at" is 30 minutes after the request
    And the token's "used_at" is empty
    And "EmailService.sendPasswordResetEmail" is called once for "john@example.com" with a link containing the plaintext token
    And an audit event "password-reset-request" is logged for "johndoe" without the token

  @story6-ac3 @api
  Scenario: An unregistered email creates no token and sends no email
    When the visitor sends "POST /api/auth/password-reset/request" with email "nobody@example.com"
    Then no password reset token is stored
    And "EmailService.sendPasswordResetEmail" is not called

  @story6-ac4 @ui
  Scenario: Visitor requests a reset link from the forgot-password page
    Given the visitor is on the "/login" page
    When the visitor follows the "Forgot password?" link
    Then the visitor is on the "/forgot-password" page
    When the visitor submits email "nobody@example.com"
    Then the message "If that email is registered, a password reset link has been sent." is displayed
