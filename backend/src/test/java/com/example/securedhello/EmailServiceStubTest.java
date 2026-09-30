package com.example.securedhello;

import static com.example.securedhello.support.LogCapture.field;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.example.securedhello.logging.LogSanitizer;
import com.example.securedhello.notification.EmailService;
import com.example.securedhello.support.LogCapture;

/**
 * The {@code EmailService} stub writes each email to its own file, standing in for a mailbox, with
 * the recipient masked, and never to the application or audit log.
 */
@SpringBootTest
@ActiveProfiles("test")
class EmailServiceStubTest {

	private static final String RECIPIENT = "testuser123@test.example.com";

	@Autowired
	EmailService emailService;

	@Test
	void theAccountLockedEmailGoesOnlyToTheEmailFileWithTheRecipientMasked() {
		LogCapture capture = LogCapture.start();

		emailService.notifyAccountLocked(RECIPIENT);

		assertThat(capture.email()).singleElement().satisfies((line) -> {
			assertThat(field(line, "log.logger")).isEqualTo("email");
			assertThat(field(line, "email.to")).isEqualTo(LogSanitizer.MASK);
			assertThat(field(line, "message")).isEqualTo("Account Locked");
		});
		assertThat(LogCapture.emailLogFile()).isNotIn(LogCapture.applicationLogFile(), LogCapture.auditLogFile());
		assertThat(capture.application(LogCapture.hasField("log.logger", "email"))).isEmpty();
		assertThat(capture.audit(LogCapture.hasField("log.logger", "email"))).isEmpty();
		assertThat(capture.applicationText()).noneMatch((line) -> line.contains(RECIPIENT));
		assertThat(capture.auditText()).noneMatch((line) -> line.contains(RECIPIENT));
	}

	@Test
	void thePasswordChangedEmailGoesOnlyToTheEmailFileWithTheRecipientMasked() {
		LogCapture capture = LogCapture.start();

		emailService.notifyPasswordChanged(RECIPIENT);

		assertThat(capture.email()).singleElement().satisfies((line) -> {
			assertThat(field(line, "email.to")).isEqualTo(LogSanitizer.MASK);
			assertThat(field(line, "message")).isEqualTo("Password changed");
		});
		assertThat(capture.applicationText()).noneMatch((line) -> line.contains(RECIPIENT));
		assertThat(capture.auditText()).noneMatch((line) -> line.contains(RECIPIENT));
	}

	/**
	 * lm-19: outside the {@code dev} profile the stub never writes the Reset Token anywhere, not even
	 * to its own file, because nothing but a local developer should read that file as a mailbox. The
	 * email is still recorded, with the recipient masked, and says the link was withheld. The {@code dev}
	 * side, where the link is written in full (ADR 0001), is covered by {@code DevProfileApiTest}.
	 */
	@Test
	void outsideDevTheResetLinkEmailWithholdsTheLinkAndItsToken() {
		LogCapture capture = LogCapture.start();
		String token = "synthetic-reset-token-value";
		String link = "http://localhost:3000/reset-password#token=" + token;

		emailService.sendPasswordResetLink(RECIPIENT, link);

		assertThat(capture.email()).singleElement().satisfies((line) -> {
			assertThat(field(line, "log.logger")).isEqualTo("email");
			assertThat(field(line, "email.to")).isEqualTo(LogSanitizer.MASK);
			assertThat(field(line, "message")).isEqualTo("Password reset requested");
			assertThat(field(line, "reset.link_withheld")).isEqualTo("true");
		});
		assertThat(capture.emailText()).noneMatch((line) -> line.contains(token));
		assertThat(capture.applicationText()).noneMatch((line) -> line.contains(RECIPIENT) || line.contains(token));
		assertThat(capture.auditText()).noneMatch((line) -> line.contains(RECIPIENT) || line.contains(token));
	}

	@Test
	void theResetCompletedEmailGoesOnlyToTheEmailFileWithTheRecipientMasked() {
		LogCapture capture = LogCapture.start();

		emailService.notifyPasswordResetCompleted(RECIPIENT);

		assertThat(capture.email()).singleElement().satisfies((line) -> {
			assertThat(field(line, "email.to")).isEqualTo(LogSanitizer.MASK);
			assertThat(field(line, "message")).isEqualTo("Password reset completed");
		});
		assertThat(capture.applicationText()).noneMatch((line) -> line.contains(RECIPIENT));
		assertThat(capture.auditText()).noneMatch((line) -> line.contains(RECIPIENT));
	}

}
