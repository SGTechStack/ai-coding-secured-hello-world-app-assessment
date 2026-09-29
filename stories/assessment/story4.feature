Feature: Logout
  As a logged-in user,
  I want to log out,
  So that my session is fully ended and cannot be reused.

  Background:
    Given the user "johndoe" is logged in with an active session
    And the user holds a CSRF token for that session

  @story4-ac1 @api
  Scenario: Logout invalidates the server-side session and clears the session cookie
    When the user sends "POST /api/auth/logout"
    Then the response status is 204
    And the server-side session is invalidated
    And the response expires the "SESSION" cookie
    And an audit event "user-logout" is logged for "johndoe"

  @story4-ac2 @api
  Scenario Outline: A session cookie captured before logout is rejected when replayed
    Given the user's "SESSION" cookie was captured before logout
    And the user has logged out
    When a request "<request>" is sent with the captured cookie
    Then the response status is 401

    Examples:
      | request              |
      | GET /api/hello       |
      | GET /api/auth/me     |
      | POST /api/auth/logout |

  @story4-ac3 @api
  Scenario: Logout without an authenticated session is rejected
    Given a client with no session
    When the client sends "POST /api/auth/logout"
    Then the response status is 401

  @story4-ac4 @ui
  Scenario: User logs out from the landing page
    Given the user is viewing the landing page "/"
    When the user clicks the "Log out" button
    Then the user is redirected to "/login"
    When the user navigates back to "/"
    Then the application redirects the user to "/login"
