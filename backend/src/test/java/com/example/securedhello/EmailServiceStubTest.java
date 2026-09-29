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
	 * ADR 0001: the reset link, Reset Token included, is written in full to the stub's own file, so it
	 * can be used locally; the recipient is still masked like every other email.
	 */
	@Test
	void theResetLinkEmailGoesOnlyToTheEmailFileWithTheRecipientMaskedAndTheLinkInFull() {
		LogCapture capture = LogCapture.start();
		String link = "http://localhost:3000/reset-password#token=synthetic-reset-token-value";

		emailService.sendPasswordResetLink(RECIPIENT, link);

		assertThat(capture.email()).singleElement().satisfies((line) -> {
			assertThat(field(line, "log.logger")).isEqualTo("email");
			assertThat(field(line, "email.to")).isEqualTo(LogSanitizer.MASK);
			assertThat(field(line, "reset.link")).isEqualTo(link);
			assertThat(field(line, "message")).isEqualTo("Password reset requested");
		});
		assertThat(capture.applicationText()).noneMatch((line) -> line.contains(RECIPIENT) || line.contains(link));
		assertThat(capture.auditText()).noneMatch((line) -> line.contains(RECIPIENT) || line.contains(link));
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
