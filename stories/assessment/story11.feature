Feature: Admin Deletes Accounts
  As an admin,
  I want to delete another user's account,
  So that I can remove accounts that should no longer exist.

  Background:
    Given an enabled ADMIN account "admin"
    And an enabled USER account "johndoe" with password "Password123!"
    And the user "admin" is logged in with a valid CSRF token

  @story11-ac1 @api
  Scenario: An admin deletes another user's account
    Given "johndoe" has an outstanding password reset token
    When the admin sends "DELETE /api/admin/users/{johndoe.id}"
    Then the response status is 204
    And no account "johndoe" exists
    And no password reset token for "johndoe" exists
    And "GET /api/admin/users" no longer lists "johndoe"
    And an audit event "user-delete" is logged with actor "admin" and target "johndoe"

  @story11-ac2 @api
  Scenario: A deleted user can no longer log in or use an existing session
    Given "johndoe" has an active session
    When the admin deletes the account "johndoe"
    Then the session of "johndoe" is rejected with 401 on its next request
    And a login for "johndoe" with password "Password123!" returns 401 with '{"message": "Invalid username or password"}'

  @story11-ac3 @api
  Scenario: An admin cannot delete their own account
    When the admin sends "DELETE /api/admin/users/{admin.id}"
    Then the response status is 400
    And the response body is '{"message": "You cannot delete your own account"}'
    And the account "admin" still exists

  @story11-ac4 @api
  Scenario: Deleting an unknown user returns not found
    When the admin sends "DELETE /api/admin/users/00000000-0000-0000-0000-000000000000"
    Then the response status is 404

  @story11-ac5 @ui
  Scenario: An admin deletes a user after confirming
    Given the admin is on the "/admin/users" page
    When the admin clicks "Delete" on the row for "johndoe"
    Then a confirmation dialog asks "Delete user johndoe? This cannot be undone."
    When the admin confirms the deletion
    Then the row for "johndoe" is removed from the table
    And the row for "admin" offers no delete control
