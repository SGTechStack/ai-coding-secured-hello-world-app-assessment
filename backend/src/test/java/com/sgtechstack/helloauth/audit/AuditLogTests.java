package com.sgtechstack.helloauth.audit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuditLogTests {

	@Test
	void controlCharactersAreEscapedSoValuesCannotForgeLogLines() {
		String forged = AuditLog.sanitize("bob\nevent=ADMIN_BOOTSTRAPPED actor=root\r");

		assertThat(forged).doesNotContain("\n").doesNotContain("\r").startsWith("bob\\u000aevent=");
	}

	@Test
	void quotesAndBackslashesAreEscaped() {
		assertThat(AuditLog.sanitize("a\"b\\c")).isEqualTo("a\\\"b\\\\c");
	}

	@Test
	void longValuesAreTruncated() {
		assertThat(AuditLog.sanitize("x".repeat(500))).hasSize(131).endsWith("...");
	}

	@Test
	void missingValuesAreRenderedAsDash() {
		assertThat(AuditLog.sanitize(null)).isEqualTo("-");
	}

}
