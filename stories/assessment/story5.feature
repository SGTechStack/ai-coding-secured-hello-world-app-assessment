Feature: Personalized Greeting
  As a logged-in user,
  I want to see a personalized greeting,
  So that I can confirm my authentication actually worked.

  @story5-ac1 @api
  Scenario Outline: An authenticated user is greeted by username
    Given the user "<username>" with role "<role>" is logged in with an active session
    When the user sends "GET /api/hello"
    Then the response status is 200
    And the response body is "Hello, <username>"

    Examples:
      | username | role  |
      | johndoe  | USER  |
      | admin    | ADMIN |

  @story5-ac2 @api
  Scenario Outline: A request without a valid session is unauthorized
    Given a client with <session>
    When the client sends "GET /api/hello"
    Then the response status is 401
    And the response body contains no greeting

    Examples:
      | session                                            |
      | no session cookie                                  |
      | a tampered session cookie                          |
      | a session that has exceeded the idle timeout       |
      | a session that has exceeded the absolute lifetime  |

  @story5-ac3 @ui
  Scenario: The landing page shows the greeting returned by the API
    Given the user "johndoe" is logged in with an active session
    When the landing page "/" is rendered
    Then the page displays "Hello, johndoe"

  @story5-ac4 @ui
  Scenario: An unauthenticated visitor opening the landing page is sent to login
    Given the visitor does not have a valid session
    When the visitor navigates directly to "/"
    Then the application redirects the visitor to "/login"

  @story5-ac5 @ui
  Scenario: The authenticated view survives a browser reload via the session cookie
    Given the user is viewing the landing page "/" with an active session
    When the user reloads the page
    Then the landing page still shows "Hello, johndoe"
    And the user is not redirected to "/login"

  @story5-ac6 @ui
  Scenario: An authenticated user opening the login page is sent to the landing page
    Given the user has an active session
    When the user navigates directly to "/login"
    Then the application redirects the user to "/"
