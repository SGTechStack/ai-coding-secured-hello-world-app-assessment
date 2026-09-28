@story-1 @registration
Feature: Story 1 - Visitor registration
  As a visitor
  I want to register an account with a username, email, and password
  So that I can log in and access the protected app

  @ui
  Scenario: A visitor registers through the UI and can then log in
    Given a visitor has chosen new account details "alice"
    When the visitor registers "alice" through the registration form
    Then the login form is shown
    And the account "alice" exists with role "USER" and enabled status "TRUE"
    And the stored password of "alice" is a BCrypt hash of their password
    When "alice" logs in through the login form
    Then the page greets "alice"

  Scenario: Registering via the API creates an enabled USER account with a BCrypt password hash
    Given a visitor has chosen new account details "bob"
    When the visitor registers "bob" via the API
    Then the response status is 201
    And the response JSON field "username" is "{username:bob}"
    And the account "bob" exists with role "USER" and enabled status "TRUE"
    And the stored password of "bob" is a BCrypt hash of their password
    And the plaintext password of "bob" does not appear in the backend log

  Scenario Outline: A registration reusing an existing <field> is rejected as a conflict
    Given a registered user "carol"
    And a visitor has chosen new account details "dave"
    When the visitor registers "dave" via the API reusing the <field> of "carol"
    Then the response status is 409
    And the response error message is "<message>"
    And no account was created from the rejected registration

    Examples:
      | field    | message                       |
      | username | Username is already registered |
      | email    | Email is already registered    |

  @ui
  Scenario: The registration form shows a conflict error for a taken username
    Given a registered user "erin"
    And a visitor has chosen new account details "frank"
    When the visitor registers "frank" through the registration form reusing the username of "erin"
    Then the page shows the alert "Username is already registered"

  Scenario Outline: A password that fails the strength policy is rejected
    Given a visitor has chosen new account details "gina" with password "<password>"
    When the visitor registers "gina" via the API
    Then the response status is 400
    And the response JSON field "fields.password" is "Password must be at least 12 characters long"
    And no account was created from the rejected registration
    And the plaintext password of "gina" does not appear in the backend log

    # Distinctive values so the "not in the backend log" check is meaningful.
    Examples:
      | password    |
      | Short-Pw-11 |
      | Zq9!x       |

  @ui
  Scenario: The registration form refuses a password shorter than 12 characters
    Given a visitor has chosen new account details "hank" with password "Short-Pw-11"
    When the visitor registers "hank" through the registration form
    Then the registration form is still shown
    And no account was created from the rejected registration
