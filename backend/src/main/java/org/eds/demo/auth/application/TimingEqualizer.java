package org.eds.demo.auth.application;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Spends the cost of one password check on refusals that never reach the real one, so response time
 * does not reveal Account state (delayed, expired Temporary Password). The dummy hash is computed
 * once at startup with the live encoder, so it costs the same as a real stored hash.
 */
@Component
class TimingEqualizer {

  /** Arbitrary text; only the cost of hashing and comparing it matters. */
  private static final String DUMMY_PASSWORD = "timing-equalizer-dummy-password";

  private final PasswordEncoder passwordEncoder;
  private final String dummyHash;

  TimingEqualizer(PasswordEncoder passwordEncoder) {
    this.passwordEncoder = passwordEncoder;
    this.dummyHash = passwordEncoder.encode(DUMMY_PASSWORD);
  }

  /** Performs one password comparison against the dummy hash and discards the result. */
  void spendPasswordCheck(String suppliedPassword) {
    passwordEncoder.matches(suppliedPassword, dummyHash);
  }
}
