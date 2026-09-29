Feature: Password Reset Confirmation
  As a user with a valid reset token,
  I want to set a new password,
  So that I can regain access to my account.

  # Every rejected token (unknown, expired, used) gets the same response so tokens cannot be probed.

  Background:
    Given an account "johndoe" with password "Password123!"
    And "johndoe" was issued the reset token "valid-reset-token" 10 minutes ago with a 30 minute lifetime
    And the visitor holds a CSRF token from "GET /api/csrf"

  @story7-ac1 @api
  Scenario: A valid token sets the new password, is consumed, and ends all existing sessions
    Given "johndoe" has an active session on another device
    When the visitor sends "POST /api/auth/password-reset/confirm" with token "valid-reset-token" and new password "a-brand-new-passphrase"
    Then the response status is 204
    And the account's "password_hash" is a BCrypt hash that matches "a-brand-new-passphrase"
    And the token's "used_at" is set
    And the other device's session is rejected with 401 on its next request
    And logging in as "johndoe" with "Password123!" fails
    And logging in as "johndoe" with "a-brand-new-passphrase" succeeds
    And an audit event "password-reset-complete" is logged for "johndoe"

  @story7-ac2 @api
  Scenario: An expired token is rejected and the password is unchanged
    Given 31 minutes have elapsed since the token was issued
    When the visitor sends "POST /api/auth/password-reset/confirm" with token "valid-reset-token" and new password "a-brand-new-passphrase"
    Then the response status is 400
    And the response body is '{"message": "Invalid or expired reset token"}'
    And the account's password still matches "Password123!"

  @story7-ac3 @api
  Scenario: A token that has already been used is rejected
    Given the token "valid-reset-token" was used to set the password to "a-brand-new-passphrase"
    When the visitor sends "POST /api/auth/password-reset/confirm" with token "valid-reset-token" and new password "yet-another-passphrase"
    Then the response status is 400
    And the response body is '{"message": "Invalid or expired reset token"}'
    And the account's password still matches "a-brand-new-passphrase"

  @story7-ac4 @api
  Scenario: An unknown token is rejected
    When the visitor sends "POST /api/auth/password-reset/confirm" with token "not-a-real-token" and new password "a-brand-new-passphrase"
    Then the response status is 400
    And the response body is '{"message": "Invalid or expired reset token"}'
    And the account's password still matches "Password123!"

  @story7-ac5 @api
  Scenario: A new password that fails the strength policy is rejected without consuming the token
    When the visitor sends "POST /api/auth/password-reset/confirm" with token "valid-reset-token" and new password "short"
    Then the response status is 400
    And the response body is '{"message": "Password must be at least 12 characters"}'
    And the token's "used_at" is empty
    And the account's password still matches "Password123!"

  @story7-ac6 @ui
  Scenario: User sets a new password from the emailed reset link
    Given the visitor opens "/reset-password?token=valid-reset-token"
    When the visitor enters and confirms the new password "a-brand-new-passphrase"
    And clicks the "Reset password" button
    Then the visitor is redirected to "/login"
    And the message "Your password has been reset. Please log in." is displayed
