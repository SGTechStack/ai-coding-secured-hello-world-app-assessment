Feature: Admin Changes User Roles
  As an admin,
  I want to change another user's role between USER and ADMIN,
  So that I can grant or revoke admin privileges.

  # Roles are exactly USER and ADMIN. The legacy USER_MANAGER role is no longer valid.
  # A role change takes effect on the target's very next request; stale session authorities are not trusted.

  Background:
    Given an enabled ADMIN account "admin"
    And an enabled ADMIN account "otheradmin"
    And an enabled USER account "johndoe"
    And the user "admin" is logged in with a valid CSRF token

  @story10-ac1 @api
  Scenario: An admin promotes a USER to ADMIN
    Given "johndoe" has an active session
    When the admin sends "PATCH /api/admin/users/{johndoe.id}/role" with body '{"role": "ADMIN"}'
    Then the response status is 200
    And the response body shows "johndoe" with role "ADMIN"
    And the account "johndoe" has role "ADMIN"
    And an audit event "user-role-change" is logged with actor "admin", target "johndoe", from "USER" and to "ADMIN"

  @story10-ac2 @api
  Scenario: A demoted admin loses admin access on their next request
    Given "otheradmin" has an active session
    When the admin sends "PATCH /api/admin/users/{otheradmin.id}/role" with body '{"role": "USER"}'
    Then the response status is 200
    And the account "otheradmin" has role "USER"
    And the session of "otheradmin" gets 403 on its next "GET /api/admin/users"

  @story10-ac3 @api
  Scenario Outline: An invalid role value is rejected
    When the admin sends "PATCH /api/admin/users/{johndoe.id}/role" with body '<body>'
    Then the response status is 400
    And the account "johndoe" has role "USER"

    Examples:
      | body                        |
      | {}                          |
      | {"role": null}              |
      | {"role": "SUPERUSER"}       |
      | {"role": "USER_MANAGER"}    |

  @story10-ac4 @api
  Scenario: An admin cannot change their own role
    When the admin sends "PATCH /api/admin/users/{admin.id}/role" with body '{"role": "USER"}'
    Then the response status is 400
    And the response body is '{"message": "You cannot change your own role"}'
    And the account "admin" has role "ADMIN"

  @story10-ac5 @api
  Scenario: Changing the role of an unknown user returns not found
    When the admin sends "PATCH /api/admin/users/00000000-0000-0000-0000-000000000000/role" with body '{"role": "ADMIN"}'
    Then the response status is 404

  @story10-ac6 @ui
  Scenario: An admin changes a user's role from the user management page
    Given the admin is on the "/admin/users" page
    When the admin sets the role of "johndoe" to "ADMIN"
    Then the row for "johndoe" shows role "ADMIN"
    And the row for "admin" offers no role control
