Feature: Username and Password Login
  As a registered user,
  I want to log in with my username and password,
  So that I can access my session and the protected app content.

  # Every failed login returns the same generic 401 so the response never reveals whether the
  # username exists, is locked, or is disabled. Scenarios 7-14 specify the login form itself
  # (inline validation, loading state, error banners).

  Background:
    Given an enabled account "johndoe" with password "Password123!"
    And the user holds a CSRF token from "GET /api/csrf"

  @story2-ac1 @api
  Scenario: Correct credentials create a server-side session and reset the failure counter
    Given the account "johndoe" has failed_login_attempts 3
    When the user sends "POST /api/auth/login" with username "johndoe" and password "Password123!"
    Then the response status is 200
    And the response body contains username "johndoe" and role "USER"
    And a server-side session is created for "johndoe"
    And the response sets an HttpOnly "SESSION" cookie
    And the account "johndoe" has failed_login_attempts 0

  @story2-ac2 @api
  Scenario: Wrong password is rejected with a generic error and increments the failure counter
    Given the account "johndoe" has failed_login_attempts 0
    When the user sends "POST /api/auth/login" with username "johndoe" and password "WrongPassword!"
    Then the response status is 401
    And the response body is '{"message": "Invalid username or password"}'
    And no authenticated session is created
    And the account "johndoe" has failed_login_attempts 1

  @story2-ac3 @api
  Scenario: Unknown username is rejected with a response identical to a wrong password
    When the user sends "POST /api/auth/login" with username "nosuchuser" and password "Password123!"
    Then the response status is 401
    And the response body is '{"message": "Invalid username or password"}'
    And the response status, headers and body match those of a wrong-password attempt for "johndoe"
    And no account's failed_login_attempts changes

  @story2-ac4 @api
  Scenario: A locked account is rejected even with correct credentials
    Given the account "johndoe" has locked_until 10 minutes in the future
    When the user sends "POST /api/auth/login" with username "johndoe" and password "Password123!"
    Then the response status is 401
    And the response body is '{"message": "Invalid username or password"}'
    And no authenticated session is created

  @story2-ac5 @api
  Scenario: A disabled account is rejected even with correct credentials
    Given the account "johndoe" has enabled "false"
    When the user sends "POST /api/auth/login" with username "johndoe" and password "Password123!"
    Then the response status is 401
    And the response body is '{"message": "Invalid username or password"}'
    And no authenticated session is created

  @story2-ac6 @api
  Scenario: Username matching at login is case-insensitive
    When the user sends "POST /api/auth/login" with username "JohnDoe" and password "Password123!"
    Then the response status is 200
    And a server-side session is created for "johndoe"

  @story2-ac7 @ui
  Scenario: Successful login from the form redirects to the landing page
    Given the user is on the "/login" page
    When the user enters username "johndoe" and password "Password123!"
    And clicks the "Log in" button
    Then the user is redirected to the landing page "/"

  @story2-ac8 @ui
  Scenario: Submission is blocked when the username or password is empty
    Given the user is on the "/login" page
    When the user leaves the username or password field blank
    And clicks the "Log in" button
    Then no request is sent to "/api/auth/login"
    And inline validation errors are displayed beneath the empty fields:
      | field    | error message          |
      | username | "Username is required" |
      | password | "Password is required" |

  @story2-ac9 @ui
  Scenario: Validation errors do not appear while the user is typing before submitting
    Given the user is on the "/login" page
    When the user types invalid characters into the username field
    And has not yet clicked the "Log in" button
    Then no inline validation errors or error banners are displayed

  @story2-ac10 @ui
  Scenario: An inline error disappears as soon as its field changes
    Given an inline validation error is displayed under the username field
    When the user types any character into the username field
    Then the inline validation error beneath the username field is dismissed immediately

  @story2-ac11 @ui
  Scenario: The form is disabled and shows loading feedback for at least 400ms
    Given the user submits validly formatted credentials
    When the login request is in flight
    Then the username input, password input and submit button become disabled
    And the submit button text changes to "Logging in..." with an animating ellipsis
    And the disabled and loading state lasts at least 400ms regardless of network speed

  @story2-ac12 @ui
  Scenario: Incorrect credentials show a generic, non-leaking failure banner
    Given the user submits an invalid username or password
    When "/api/auth/login" responds with 401 and body '{"message": "Invalid username or password"}'
    Then an error banner displaying "Invalid username or password" appears above the login form
    And no message reveals whether the username or password caused the failure
    And the form inputs and submit button are re-enabled

  @story2-ac13 @ui
  Scenario: The failure banner disappears when either field is edited
    Given the authentication failure banner is displayed above the login form
    When the user types into either the username or password field
    Then the authentication failure banner is dismissed immediately

  @story2-ac14 @ui
  Scenario: A distinct banner is shown for server errors and connection outages
    Given the user submits validly formatted credentials
    When the request fails due to a network outage or a 500 response
    Then an error banner displaying "Unable to connect to the server. Please try again later." is shown above the login form
    And the form inputs and submit button are re-enabled
