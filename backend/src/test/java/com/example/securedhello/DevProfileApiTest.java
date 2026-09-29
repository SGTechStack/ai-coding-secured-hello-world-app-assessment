package com.example.securedhello;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** The dev profile's local-only carve-outs, against a throwaway in-memory database. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:dev-profile-test;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class DevProfileApiTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	Environment environment;

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

}
