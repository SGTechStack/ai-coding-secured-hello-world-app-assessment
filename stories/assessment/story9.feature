Feature: Admin Enables or Disables Accounts
  As an admin,
  I want to enable or disable another user's account,
  So that I can suspend access without deleting their data.

  Background:
    Given an enabled ADMIN account "admin"
    And an enabled USER account "johndoe" with password "Password123!"
    And the user "admin" is logged in with a valid CSRF token

  @story9-ac1 @api
  Scenario: An admin disables another user's account
    When the admin sends "PATCH /api/admin/users/{johndoe.id}/status" with body '{"enabled": false}'
    Then the response status is 200
    And the response body shows "johndoe" with enabled "false"
    And the account "johndoe" has enabled "false"
    And an audit event "user-disable" is logged with actor "admin" and target "johndoe"

  @story9-ac2 @api
  Scenario: A disabled user can no longer log in
    Given the account "johndoe" has been disabled by "admin"
    When a login for "johndoe" is attempted with password "Password123!"
    Then the response status is 401
    And the response body is '{"message": "Invalid username or password"}'

  @story9-ac3 @api
  Scenario: Disabling a user ends their existing sessions
    Given "johndoe" has an active session
    When the admin disables the account "johndoe"
    Then the session of "johndoe" is rejected with 401 on its next request

  @story9-ac4 @api
  Scenario: Re-enabling a disabled account restores login
    Given the account "johndoe" has been disabled by "admin"
    When the admin sends "PATCH /api/admin/users/{johndoe.id}/status" with body '{"enabled": true}'
    Then the response status is 200
    And an audit event "user-enable" is logged with actor "admin" and target "johndoe"
    And logging in as "johndoe" with "Password123!" succeeds

  @story9-ac5 @api
  Scenario: An admin cannot change the status of their own account
    When the admin sends "PATCH /api/admin/users/{admin.id}/status" with body '{"enabled": false}'
    Then the response status is 400
    And the response body is '{"message": "You cannot change the status of your own account"}'
    And the account "admin" has enabled "true"

  @story9-ac6 @api
  Scenario Outline: Invalid status change requests are rejected
    When the admin sends "PATCH /api/admin/users/<target>/status" with body '<body>'
    Then the response status is <status>
    And no account is changed

    Examples:
      | target                                 | body                 | status |
      | {johndoe.id}                           | {}                   | 400    |
      | {johndoe.id}                           | {"enabled": "maybe"} | 400    |
      | 00000000-0000-0000-0000-000000000000   | {"enabled": false}   | 404    |

  @story9-ac7 @ui
  Scenario: An admin disables a user from the user management page
    Given the admin is on the "/admin/users" page
    When the admin clicks "Disable" on the row for "johndoe"
    Then the row for "johndoe" shows status "Disabled" and offers "Enable"
    And the row for "admin" offers no status control
