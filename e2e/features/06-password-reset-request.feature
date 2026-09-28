@story-6 @password-reset
Feature: Story 6 - Request a password reset
  As a user who forgot their password
  I want to request a password reset via my registered email
  So that I can regain access without contacting an admin

  Background:
    Given a registered user "alice"

  Scenario: Registered and unregistered emails get the identical generic response
    When an anonymous client requests a password reset for the email of "alice"
    And the response is remembered as "registered"
    And an anonymous client requests a password reset for an unregistered email
    And the response is remembered as "unregistered"
    Then the remembered responses "registered" and "unregistered" are identical
    And the remembered response "registered" has status 200 and JSON field "message" equal to "If that email is registered, a reset link has been sent"

  Scenario: A reset request for a registered email stores only a hashed, short-lived, single-use token
    When an anonymous client requests a password reset for the email of "alice"
    Then the response status is 200
    And exactly 1 reset token exists for "alice"
    And the latest reset token of "alice" is unused
    And the latest reset token of "alice" is stored as a BCrypt hash
    And the latest reset token of "alice" expires in between 15 and 30 minutes
    And the backend log records "Password reset requested username={username:alice}"
    And the latest reset token secret of "alice" does not appear in the backend log

  @ui
  Scenario Outline: The forgot-password form shows the same generic confirmation for <case>
    When an anonymous visitor requests a password reset through the form for <email>
    Then the page shows the status "If that email is registered, a reset link has been sent"

    Examples:
      | case                 | email                   |
      | a registered email   | the email of "alice"    |
      | an unregistered email | an unregistered email  |
