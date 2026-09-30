@story-10 @admin
Feature: Story 10 - Admin changes another user's role
  As an admin
  I want to change another user's role between USER and ADMIN
  So that I can grant or revoke admin privileges

  Background:
    Given a registered admin "root"
    And a registered user "alice"
    And "root" is logged in

  Scenario: Promoting another user to ADMIN grants admin access; demoting revokes it
    When "root" sends PATCH "/api/admin/users/{id:alice}/role" with:
      """
      { "role": "ADMIN" }
      """
    Then the response status is 200
    And the account "alice" exists with role "ADMIN" and enabled status "TRUE"
    Given "alice" is logged in
    When "alice" sends GET "/api/admin/users"
    Then the response status is 200
    When "root" sends PATCH "/api/admin/users/{id:alice}/role" with:
      """
      { "role": "USER" }
      """
    Then the response status is 200
    And the account "alice" exists with role "USER" and enabled status "TRUE"

  Scenario: An admin cannot demote themselves
    When "root" sends PATCH "/api/admin/users/{id:root}/role" with:
      """
      { "role": "USER" }
      """
    Then the response status is 400
    And the response error message is "An admin cannot change their own role"
    And the account "root" exists with role "ADMIN" and enabled status "TRUE"

  # KNOWN DEFECT: AdminUserController calls Role.valueOf() on the raw string;
  # an unknown role throws IllegalArgumentException, which the catch-all
  # handler maps to 500 instead of a 400 validation error.
  @known-defect
  Scenario: A role outside USER/ADMIN is rejected as a client error and the role is unchanged
    When "root" sends PATCH "/api/admin/users/{id:alice}/role" with:
      """
      { "role": "SUPERUSER" }
      """
    Then the response status is 400
    And the account "alice" exists with role "USER" and enabled status "TRUE"

  @ui
  Scenario: The admin promotes a user from the user table
    Given "root" is logged in through the login form
    When the user clicks "Manage users"
    And the admin clicks "Make ADMIN" in the row of "alice"
    And the admin clicks "Grant admin" in the row of "alice"
    Then the users table shows "alice" with role "ADMIN" and enabled "Yes"
    And the account "alice" exists with role "ADMIN" and enabled status "TRUE"

  @ui
  Scenario: Granting admin from the user table requires confirmation and can be cancelled
    Given "root" is logged in through the login form
    When the user clicks "Manage users"
    And the admin clicks "Make ADMIN" in the row of "alice"
    Then the users table shows "alice" with role "USER" and enabled "Yes"
    When the admin clicks "Cancel" in the row of "alice"
    Then the users table shows "alice" with role "USER" and enabled "Yes"
    And the account "alice" exists with role "USER" and enabled status "TRUE"
