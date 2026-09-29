Feature: Initial Admin Bootstrap
  As an operator deploying the app for the first time,
  I want an initial admin account to be created automatically,
  So that there's a way into the admin module without manual database edits.

  # Credentials come only from configuration (app.admin.username, app.admin.email, app.admin.password),
  # typically via environment variables. There is no built-in default password.

  @story12-ac1 @api
  Scenario: The first start seeds an admin from configuration
    Given no account with role "ADMIN" exists
    And the configuration sets:
      | property           | value                    |
      | app.admin.username | admin                    |
      | app.admin.email    | admin@example.com        |
      | app.admin.password | bootstrap-admin-secret-1 |
    When the application starts
    Then exactly one account with role "ADMIN" exists
    And the account "admin" has email "admin@example.com" and enabled "true"
    And the account's "password_hash" is a BCrypt hash that matches "bootstrap-admin-secret-1"
    And logging in as "admin" with "bootstrap-admin-secret-1" succeeds
    And no log line contains "bootstrap-admin-secret-1"

  @story12-ac2 @api
  Scenario: A restart does not create a duplicate or overwrite the existing admin
    Given an account "admin" with role "ADMIN" and password "changed-by-admin-later"
    And the configuration sets "app.admin.password" to "bootstrap-admin-secret-1"
    When the admin bootstrap runs again on application start
    Then exactly one account with role "ADMIN" exists
    And the account "admin" still has password "changed-by-admin-later"

  @story12-ac3 @api
  Scenario: Startup fails fast when no admin exists and admin credentials are not configured
    Given no account with role "ADMIN" exists
    And "app.admin.password" is not configured
    When the application starts
    Then startup fails with an error naming the missing "app.admin.password" property
    And no account is created

  @story12-ac4 @api
  Scenario: Startup fails when the configured admin password fails the strength policy
    Given no account with role "ADMIN" exists
    And the configuration sets "app.admin.password" to "short"
    When the application starts
    Then startup fails with an error stating the admin password does not meet the password policy
    And the error does not contain the configured password
    And no account is created
