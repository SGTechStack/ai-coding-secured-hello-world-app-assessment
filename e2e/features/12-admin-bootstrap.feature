@story-12 @bootstrap
Feature: Story 12 - Initial admin bootstrap
  As an operator deploying the app for the first time
  I want an initial admin account to be created automatically
  So that there's a way into the admin module without manual database edits

  Scenario: The configured bootstrap admin was seeded with a normally hashed password
    Then exactly one account exists with the configured bootstrap admin username
    And the bootstrap admin account has role "ADMIN"
    And the bootstrap admin password is hashed with the same BCrypt scheme as a registered account
    And the configured bootstrap admin credentials were accepted on first login

  # A restart of the dev backend wipes the in-memory H2 database, so the
  # "ADMIN already exists" precondition cannot be reproduced black-box.
  # Covered by backend AdminBootstrapIntegrationTest instead.
  @skip
  Scenario: Restarting the application does not create a duplicate seed admin
    Given an "ADMIN" user already exists
    When the application restarts
    Then exactly one account exists with the configured bootstrap admin username

  # A real bootstrap admin's forcePasswordChange flag can only be observed
  # once per backend process — global-setup consumes and rotates it before
  # any scenario runs (see support/global-setup.ts) — so this puts the same
  # forcePasswordChange=true state onto a disposable registered user instead
  # of the real bootstrap admin, exercising the identical filter/UI path
  # (ForcePasswordChangeFilter + frontend ChangePasswordForm).
  @ui
  Scenario: A user with a forced password change is walked through changing it before reaching the app
    Given a registered user "iris" with a forced password change pending
    When "iris" logs in through the login form
    Then the change-password form is shown
    When "iris" changes their password from their original password to "Brand-New-Forced-Pass-2026" through the form
    Then the page greets "iris"
    And "iris" can log in with the password "Brand-New-Forced-Pass-2026"
    And "iris" cannot log in with their original password
