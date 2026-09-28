@story-2 @login
Feature: Story 2 - Login with username and password
  As a registered user
  I want to log in with my username and password
  So that I can access my session and the protected app content

  Background:
    Given a registered user "alice"

  @ui
  Scenario: Correct credentials in the UI create a session and show the protected greeting
    When "alice" logs in through the login form
    Then the page greets "alice"
    And the browser holds an HttpOnly "JSESSIONID" session cookie

  Scenario: A successful login creates a session and resets the failed-attempt counter
    Given someone has failed to log in as "alice" 2 times
    And "alice" has 2 failed login attempts recorded
    When "alice" logs in with the correct password
    Then the response status is 200
    And the response sets an HttpOnly "JSESSIONID" cookie
    And "alice" can access "/api/hello"
    And "alice" has 0 failed login attempts recorded

  Scenario: A wrong password is rejected generically and increments the failed-attempt counter
    When someone logs in as "alice" with a wrong password
    Then the response status is 401
    And the response error message is "Invalid username or password"
    And "alice" has 1 failed login attempts recorded

  Scenario: Wrong password and unknown username produce identical responses
    When someone logs in as "alice" with a wrong password
    And the response is remembered as "wrong password"
    And someone logs in with a username that is not registered
    And the response is remembered as "unknown username"
    Then the remembered responses "wrong password" and "unknown username" are identical

  @ui
  Scenario Outline: The login form shows the same generic error for <case>
    When someone submits the login form for <who> with a wrong password
    Then the page shows the alert "Invalid username or password"
    And the page does not greet anyone

    Examples:
      | case             | who                        |
      | a wrong password | "alice"                    |
      | an unknown user  | a username that is unknown |

  Scenario: A locked account is rejected even with the correct password
    Given the account "alice" is locked out
    When "alice" logs in with the correct password
    Then the response status is 401
    And the response error message is "Invalid username or password"
    And "alice" cannot access "/api/hello"
