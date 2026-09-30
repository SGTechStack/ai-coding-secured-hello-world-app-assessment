@story-11 @admin
Feature: Story 11 - Admin deletes another user's account
  As an admin
  I want to delete another user's account
  So that I can remove accounts that should no longer exist

  Background:
    Given a registered admin "root"
    And a registered user "alice"
    And "root" is logged in

  Scenario: Deleting another user removes the account
    Given the id of "alice" is known
    When "root" sends DELETE "/api/admin/users/{id:alice}"
    Then the response status is 200
    And the user list seen by "root" does not contain "alice"
    And no database record exists for "alice"
    When "alice" logs in with the correct password
    Then the response status is 401

  Scenario: An admin cannot delete their own account
    When "root" sends DELETE "/api/admin/users/{id:root}"
    Then the response status is 400
    And the response error message is "An admin cannot delete their own account"
    And the account "root" exists with role "ADMIN" and enabled status "TRUE"

  @ui
  Scenario: The admin deletes a user from the user table
    Given "root" is logged in through the login form
    When the user clicks "Manage users"
    And the admin clicks "Delete" in the row of "alice"
    And the admin clicks "Confirm delete" in the row of "alice"
    Then the users table does not show "alice"
    And no database record exists for "alice"

  @ui
  Scenario: Deleting from the user table requires confirmation and can be cancelled
    Given "root" is logged in through the login form
    When the user clicks "Manage users"
    And the admin clicks "Delete" in the row of "alice"
    Then the users table shows "alice" with role "USER" and enabled "Yes"
    When the admin clicks "Cancel" in the row of "alice"
    Then the users table shows "alice" with role "USER" and enabled "Yes"
    And the account "alice" exists with role "USER" and enabled status "TRUE"
