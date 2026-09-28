@story-7 @password-reset
Feature: Story 7 - Set a new password with a reset token
  As a user with a valid reset token
  I want to set a new password
  So that I can regain access to my account

  Background:
    Given a registered user "alice"
    And "alice" has requested a password reset and received the reset link

  @ui
  Scenario: A valid token resets the password through the reset link
    When "alice" opens the reset link and sets the new password "Brand-New-Password-2026"
    Then the page shows the status "Your password has been reset. You can now log in."
    And the latest reset token of "alice" is marked used
    And "alice" can log in with the password "Brand-New-Password-2026"
    And "alice" cannot log in with their original password

  Scenario: A successful reset invalidates every existing session of the user
    Given "alice" is logged in on devices "laptop" and "phone"
    When an anonymous client confirms the reset of "alice" with the new password "Brand-New-Password-2026"
    Then the response status is 200
    And device "laptop" cannot access "/api/hello"
    And device "phone" cannot access "/api/hello"

  Scenario: An expired token is rejected and the password is unchanged
    Given the latest reset token of "alice" has expired
    When an anonymous client confirms the reset of "alice" with the new password "Brand-New-Password-2026"
    Then the response status is 400
    And the response error message is "Reset token has expired"
    And "alice" can log in with their original password

  Scenario: A token can only be used once
    When an anonymous client confirms the reset of "alice" with the new password "Brand-New-Password-2026"
    Then the response status is 200
    When an anonymous client confirms the reset of "alice" with the new password "Another-New-Password-2026"
    Then the response status is 400
    And the response error message is "Reset token has already been used"
    And "alice" can log in with the password "Brand-New-Password-2026"

  Scenario: A new password that fails the strength policy is rejected and the token stays usable
    When an anonymous client confirms the reset of "alice" with the new password "short"
    Then the response status is 400
    And the latest reset token of "alice" is unused
    And "alice" can log in with their original password
