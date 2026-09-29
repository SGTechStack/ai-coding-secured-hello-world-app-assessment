package com.sgtechstack.helloauth.support;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import tools.jackson.databind.json.JsonMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.sgtechstack.helloauth.user.Role;
import com.sgtechstack.helloauth.user.User;
import com.sgtechstack.helloauth.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Base for full-stack tests. All test classes share one application context and database, so
 * every test isolates itself with unique usernames and a unique client IP rather than cleanup.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestBeans.class)
public abstract class IntegrationTest {

	protected static final String PASSWORD = "Correct-Horse-Battery-9";

	private static final AtomicInteger SEQUENCE = new AtomicInteger();

	@Autowired
	protected MockMvc mockMvc;

	@Autowired
	protected JsonMapper json;

	@Autowired
	protected UserRepository users;

	@Autowired
	protected PasswordEncoder passwordEncoder;

	@Autowired
	protected MutableClock clock;

	@Autowired
	protected RecordingEmailService emails;

	protected ApiClient newClient() {
		return new ApiClient(this.mockMvc, this.json, uniqueIp());
	}

	protected ApiClient newClient(String ip) {
		return new ApiClient(this.mockMvc, this.json, ip);
	}

	protected User createUser(Role role) {
		return createUser(role, PASSWORD);
	}

	protected User createUser(Role role, String password) {
		String username = uniqueUsername();
		return this.users.save(User.create(username, username + "@example.com", this.passwordEncoder.encode(password),
				role, this.clock.instant()));
	}

	protected ApiClient loggedIn(User user) {
		ApiClient client = newClient();
		assertThat(client.login(user.getUsername(), PASSWORD).getStatus()).isEqualTo(200);
		return client;
	}

	protected User reload(User user) {
		return this.users.findById(user.getId()).orElseThrow();
	}

	protected static String uniqueUsername() {
		return "user" + SEQUENCE.incrementAndGet() + "_" + ThreadLocalRandom.current().nextInt(100_000, 1_000_000);
	}

	protected static String uniqueIp() {
		ThreadLocalRandom random = ThreadLocalRandom.current();
		return "10." + random.nextInt(256) + "." + random.nextInt(256) + "." + random.nextInt(1, 255);
	}

	protected static void assertProblem(MockHttpServletResponse response, int status, String code) {
		assertThat(response.getStatus()).as(ApiClient.body(response)).isEqualTo(status);
		assertThat(response.getContentType()).startsWith("application/problem+json");
		assertThat(ApiClient.body(response)).contains("\"code\":\"" + code + "\"");
	}

}
