@story-3 @lockout
Feature: Story 3 - Account lockout and IP throttling
  As a security-conscious operator
  I want repeated failed logins to trigger account lockout and IP-level throttling
  So that brute-force credential guessing is blunted

  Scenario: The Nth consecutive failure locks the account for the cooldown period
    Given a registered user "alice"
    When someone fails to log in as "alice" 4 times
    Then "alice" has 4 failed login attempts recorded
    And the account "alice" is not locked
    When someone fails to log in as "alice" 1 times
    Then "alice" has 5 failed login attempts recorded
    And the account "alice" is locked for about 15 minutes
    And the backend log records "Account locked username={username:alice}"

  Scenario: Login succeeds after the cooldown elapses and the counter resets
    Given a registered user "bob"
    And the account "bob" is locked out
    And the lockout of "bob" has expired
    When "bob" logs in with the correct password
    Then the response status is 200
    And "bob" has 0 failed login attempts recorded
    And the account "bob" is not locked

  # PRD Story 3, AC 3. docs/adr/no-ip-rate-limiting-waf-delegated.md records a
  # decision to delegate IP throttling to a WAF instead of the application, so
  # this scenario is expected to fail against the current build. It is tagged
  # @prd-gap so it can be excluded with --grep-invert @prd-gap.
  @prd-gap
  Scenario: Repeated failures from one IP across many usernames throttle that IP only
    Given 8 registered users named "victim"
    And a registered user "bystander"
    When a client at IP "203.0.113.7" fails to log in 3 times as each "victim" user
    Then none of the "victim" accounts are locked
    When a client at IP "203.0.113.7" logs in as "bystander" with the correct password
    Then the login is rejected as throttled
    And the account "bystander" is not locked
    And "bystander" has 0 failed login attempts recorded
    When a client at IP "198.51.100.23" logs in as "bystander" with the correct password
    Then the response status is 200
