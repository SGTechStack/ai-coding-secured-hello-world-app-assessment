Feature: Account Lockout and IP Throttling
  As a security-conscious operator,
  I want repeated failed logins to trigger account lockout and IP-level throttling,
  So that brute-force credential guessing is blunted.

  # Thresholds are configuration, not constants. The values below are the defaults.
  # The IP throttle is checked before credentials, so throttled attempts never reach the
  # per-account failure counter.

  Background:
    Given the account lockout threshold is 5 consecutive failed attempts
    And the account lockout window is 15 minutes from the first failure of a run
    And the account lockout duration is 15 minutes
    And the IP throttle allows 20 failed login attempts per IP address within 15 minutes
    And an enabled account "johndoe" with password "Password123!"

  @story3-ac1 @api
  Scenario: The fifth consecutive failure locks the account for the cooldown period
    Given the account "johndoe" has failed_login_attempts 4
    When a login for "johndoe" is attempted with password "WrongPassword!"
    Then the response status is 401
    And the response body is '{"message": "Invalid username or password"}'
    And the account "johndoe" has locked_until 15 minutes after the attempt
    And an audit event "account-lockout" is logged for "johndoe"

  @story3-ac2 @api
  Scenario: A successful login before the threshold resets the consecutive failure count
    Given 4 failed login attempts have been made for "johndoe"
    When a login for "johndoe" is attempted with password "Password123!"
    And a login for "johndoe" is then attempted with password "WrongPassword!"
    Then the account "johndoe" has failed_login_attempts 1
    And the account "johndoe" is not locked

  @story3-ac3 @api
  Scenario: After the cooldown elapses the correct password succeeds and the counter resets
    Given the account "johndoe" was locked with failed_login_attempts 5
    And 15 minutes have elapsed since the lockout
    When a login for "johndoe" is attempted with password "Password123!"
    Then the response status is 200
    And the account "johndoe" has failed_login_attempts 0
    And the account "johndoe" has locked_until cleared

  @story3-ac4 @api
  Scenario: Failures spread across many usernames from one IP trigger IP throttling
    Given 20 failed login attempts have been made from IP "203.0.113.10" spread across 10 different usernames
    And no account has reached the lockout threshold
    When a login for "johndoe" is attempted from IP "203.0.113.10" with password "Password123!"
    Then the response status is 429
    And the response has a "Retry-After" header
    And the response body is '{"message": "Too many requests"}'
    And an audit event "rate-limit" is logged with source IP "203.0.113.10"

  @story3-ac5 @api
  Scenario: Throttled attempts do not count towards any account's lockout
    Given IP "203.0.113.10" is throttled
    And the account "johndoe" has failed_login_attempts 0
    When 10 logins for "johndoe" are attempted from IP "203.0.113.10" with password "WrongPassword!"
    Then every response status is 429
    And the account "johndoe" has failed_login_attempts 0
    And the account "johndoe" is not locked

  @story3-ac6 @api
  Scenario: A throttled IP does not block the same account from a different IP
    Given IP "203.0.113.10" is throttled
    When a login for "johndoe" is attempted from IP "198.51.100.7" with password "Password123!"
    Then the response status is 200

  @story3-ac7 @api
  Scenario: IP throttling lifts once the throttle window has passed
    Given IP "203.0.113.10" is throttled
    And 15 minutes have elapsed since the last counted failure from that IP
    When a login for "johndoe" is attempted from IP "203.0.113.10" with password "Password123!"
    Then the response status is 200

  @story3-ac8 @api
  Scenario: Failures spread over longer than the lockout window do not lock the account
    Given the account "johndoe" had 4 failed login attempts, the first of them 15 minutes ago
    When a login for "johndoe" is attempted with password "WrongPassword!"
    Then the response status is 401
    And the account "johndoe" has failed_login_attempts 1
    And the account "johndoe" is not locked

  @story3-ac9 @api
  Scenario: After the cooldown elapses a single wrong password starts a new count
    Given the account "johndoe" was locked with failed_login_attempts 5
    And 15 minutes have elapsed since the lockout
    When a login for "johndoe" is attempted with password "WrongPassword!"
    Then the response status is 401
    And the account "johndoe" has failed_login_attempts 1
    And the account "johndoe" is not locked
