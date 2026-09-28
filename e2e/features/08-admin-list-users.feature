@story-8 @admin
Feature: Story 8 - Admin lists all registered users
  As an admin
  I want to see a list of all registered users
  So that I can review who has access to the system

  Background:
    Given a registered admin "root"
    And a registered user "alice"

  Scenario: The user list shows account details and never password hashes
    Given "root" is logged in
    When "root" sends GET "/api/admin/users"
    Then the response status is 200
    And the user list contains "alice" with role "USER" and enabled true
    And every entry in the user list has exactly the fields "id, username, email, role, enabled, createdAt"
    And the response body contains no password hash

  Scenario: A non-admin user is forbidden from listing users
    Given "alice" is logged in
    When "alice" sends GET "/api/admin/users"
    Then the response status is 403

  @ui
  Scenario: The admin sees the user table in the UI
    Given "root" is logged in through the login form
    When the user clicks "Manage users"
    Then the users table has the columns "Username, Email, Role, Enabled, Created, Actions"
    And the users table shows "alice" with role "USER" and enabled "Yes"
    And the users table marks the row of "root" as "(you)"

  @ui
  Scenario: A non-admin user is not offered the user-management page
    Given "alice" is logged in through the login form
    Then the page greets "alice"
    And there is no "Manage users" button
