@story-5 @hello
Feature: Story 5 - Personalized greeting on protected content
  As a logged-in user
  I want to see a personalized greeting
  So that I can confirm my authentication actually worked

  @ui
  Scenario: The protected page greets the logged-in user by name
    Given a registered user "alice"
    When "alice" logs in through the login form
    Then the page greets "alice"

  Scenario: GET /api/hello greets the authenticated user
    Given a registered user "bob"
    And "bob" is logged in
    When "bob" sends GET "/api/hello"
    Then the response status is 200
    And the response JSON field "message" is "Hello, {username:bob}"

  Scenario: GET /api/hello without a session is unauthorized
    When an anonymous client sends GET "/api/hello"
    Then the response status is 401

  Scenario: GET /api/hello with an invalid session cookie is unauthorized
    When a client with the session cookie "JSESSIONID=0123456789ABCDEF0123456789ABCDEF" sends GET "/api/hello"
    Then the response status is 401

  @ui
  Scenario: An anonymous browser is shown the login form instead of protected content
    When an anonymous visitor opens the app
    Then the login form is shown
    And the page does not greet anyone
