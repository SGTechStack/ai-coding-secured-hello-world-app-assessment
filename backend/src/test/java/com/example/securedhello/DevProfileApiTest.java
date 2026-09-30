package com.example.securedhello;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import static com.example.securedhello.support.LogCapture.field;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.example.securedhello.logging.LogSanitizer;
import com.example.securedhello.notification.EmailService;
import com.example.securedhello.support.LogCapture;

/** The dev profile's local-only carve-outs, against a throwaway in-memory database. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:dev-profile-test;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class DevProfileApiTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	Environment environment;

	@Autowired
	EmailService emailService;

	@Test
	void h2ConsoleIsServedByItsOwnChainInDev() throws Exception {
		int status = mvc.perform(get("/h2-console/"))
			// Only the dev H2-console chain relaxes framing to same-origin.
			.andExpect(header().string("X-Frame-Options", "SAMEORIGIN"))
			.andReturn()
			.getResponse()
			.getStatus();
		assertThat(status).isNotIn(401, 403);
	}

	@Test
	void sqlIsLoggedThroughTheLoggerOnlyInDev() {
		assertThat(LoggerFactory.getLogger("org.hibernate.SQL").isDebugEnabled()).isTrue();
		assertThat(environment.getProperty("spring.jpa.show-sql", Boolean.class)).isFalse();
	}

	@Test
	void sessionCookieIsNotSecureInDev() throws Exception {
		String sessionCookie = mvc.perform(get("/api/csrf"))
			.andReturn()
			.getResponse()
			.getHeaders(HttpHeaders.SET_COOKIE)
			.stream()
			.filter((c) -> c.startsWith("SESSION="))
			.findFirst()
			.orElseThrow();

		assertThat(sessionCookie).contains("HttpOnly").contains("SameSite=Lax").doesNotContain("Secure");
	}

	/**
	 * ADR 0001, limited to dev by lm-19: only here does the stub's own file stand in for a mailbox, so
	 * the reset link, Reset Token included, is written in full and can be used locally. The recipient is
	 * still masked, and the link reaches no other log.
	 */
	@Test
	void theEmailStubWritesTheResetLinkInFullOnlyInDev() {
		LogCapture capture = LogCapture.start();
		String recipient = "testuser123@test.example.com";
		String link = "http://localhost:3000/reset-password#token=synthetic-reset-token-value";

		emailService.sendPasswordResetLink(recipient, link);

		assertThat(capture.email()).singleElement().satisfies((line) -> {
			assertThat(field(line, "email.to")).isEqualTo(LogSanitizer.MASK);
			assertThat(field(line, "reset.link")).isEqualTo(link);
			assertThat(field(line, "message")).isEqualTo("Password reset requested");
		});
		assertThat(capture.applicationText()).noneMatch((line) -> line.contains(recipient) || line.contains(link));
		assertThat(capture.auditText()).noneMatch((line) -> line.contains(recipient) || line.contains(link));
	}

}
