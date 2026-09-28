@story-9 @admin
Feature: Story 9 - Admin enables or disables another user's account
  As an admin
  I want to enable or disable another user's account
  So that I can suspend access without deleting their data

  Background:
    Given a registered admin "root"
    And a registered user "alice"
    And "root" is logged in

  Scenario: Disabling another user blocks their login; re-enabling restores it
    When "root" sends PATCH "/api/admin/users/{id:alice}/status" with:
      """
      { "enabled": false }
      """
    Then the response status is 200
    And the account "alice" exists with role "USER" and enabled status "FALSE"
    When "alice" logs in with the correct password
    Then the response status is 401
    And the response error message is "Invalid username or password"
    When "root" sends PATCH "/api/admin/users/{id:alice}/status" with:
      """
      { "enabled": true }
      """
    Then the response status is 200
    And "alice" can log in with their original password

  Scenario: An admin cannot disable their own account
    When "root" sends PATCH "/api/admin/users/{id:root}/status" with:
      """
      { "enabled": false }
      """
    Then the response status is 400
    And the response error message is "An admin cannot disable their own account"
    And the account "root" exists with role "ADMIN" and enabled status "TRUE"

  @ui
  Scenario: The admin disables a user from the user table
    Given "root" is logged in through the login form
    When the user clicks "Manage users"
    And the admin clicks "Disable" in the row of "alice"
    Then the users table shows "alice" with role "USER" and enabled "No"
    And the account "alice" exists with role "USER" and enabled status "FALSE"
