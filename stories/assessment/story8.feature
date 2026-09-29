Feature: Admin User List
  As an admin,
  I want to see a list of all registered users,
  So that I can review who has access to the system.

  Background:
    Given the following accounts exist:
      | username | email             | role  | enabled |
      | admin    | admin@example.com | ADMIN | true    |
      | johndoe  | john@example.com  | USER  | true    |
      | janedoe  | jane@example.com  | USER  | false   |

  @story8-ac1 @api
  Scenario: An admin lists every user with their account details
    Given the user "admin" is logged in
    When the user sends "GET /api/admin/users"
    Then the response status is 200
    And the response lists 3 users
    And each user entry has exactly the fields "id", "username", "email", "role", "enabled" and "createdAt"
    And the entry for "janedoe" has email "jane@example.com", role "USER" and enabled "false"

  @story8-ac2 @api
  Scenario: The user list never exposes credentials or lockout internals
    Given the user "admin" is logged in
    When the user sends "GET /api/admin/users"
    Then no user entry contains a password hash or any value starting with "$2"
    And no user entry contains "failed_login_attempts" or "locked_until"

  @story8-ac3 @api
  Scenario Outline: A USER calling any admin endpoint is forbidden
    Given the user "johndoe" is logged in
    When the user sends "<method> <path>" with a valid CSRF token
    Then the response status is 403
    And no account is changed

    Examples:
      | method | path                                |
      | GET    | /api/admin/users                    |
      | PATCH  | /api/admin/users/{janedoe.id}/status |
      | PATCH  | /api/admin/users/{janedoe.id}/role   |
      | DELETE | /api/admin/users/{janedoe.id}        |

  @story8-ac4 @api
  Scenario: An unauthenticated request to the admin API is unauthorized
    Given a client with no session
    When the client sends "GET /api/admin/users"
    Then the response status is 401

  @story8-ac5 @ui
  Scenario: An admin opens the user management page
    Given the user "admin" is logged in
    And the user is viewing the landing page "/"
    When the user follows the "Manage users" link
    Then the user is on the "/admin/users" page
    And a table lists "admin", "johndoe" and "janedoe" with their email, role, status and created date

  @story8-ac6 @ui
  Scenario: A USER cannot reach the user management page
    Given the user "johndoe" is logged in
    When the landing page "/" is rendered
    Then no "Manage users" link is shown
    When the user navigates directly to "/admin/users"
    Then the application redirects the user to "/"
