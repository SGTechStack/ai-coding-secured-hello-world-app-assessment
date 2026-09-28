@story-4 @logout
Feature: Story 4 - Logout
  As a logged-in user
  I want to log out
  So that my session is fully ended and cannot be reused

  Background:
    Given a registered user "alice"

  @ui
  Scenario: Logging out in the UI ends the session
    Given "alice" is logged in through the login form
    When the user clicks "Log out"
    Then the login form is shown
    When the page is reloaded
    Then the login form is shown
    And the browser session cannot access "/api/hello"

  # KNOWN DEFECT: LogoutController invalidates the session but sends no
  # Set-Cookie that expires JSESSIONID, so the PRD's "session cookie is
  # cleared" clause is not met (the replay scenario below still passes).
  @known-defect
  Scenario: Logout invalidates the server-side session and clears the session cookie
    Given "alice" is logged in
    When "alice" sends POST "/api/logout"
    Then the response status is 200
    And the response clears the "JSESSIONID" cookie
    And "alice" cannot access "/api/hello"

  Scenario: A session cookie captured before logout is rejected when replayed
    Given "alice" is logged in
    And the session cookie of "alice" has been captured
    When "alice" sends POST "/api/logout"
    Then the response status is 200
    When the captured session cookie is replayed to "/api/hello"
    Then the response status is 401
