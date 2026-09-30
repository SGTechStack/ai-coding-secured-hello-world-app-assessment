package com.example.securedhello;

import static com.example.securedhello.support.LogCapture.field;
import static com.example.securedhello.support.LogCapture.hasField;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.example.securedhello.support.LogCapture;
import com.example.securedhello.support.MutableClock;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The admin Account list through {@code GET /api/admin/users}, like the SPA: an Admin sees every
 * Account without credential material, viewing it is audited, and a User gets 403 on every
 * {@code /api/admin/**} path however the request is crafted. The fixture starts from an empty database
 * with one Admin and one User; all data is synthetic.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(MutableClock.Config.class)
class AdminAccountListApiTest {

	private static final String ADMIN = "testadmin123";

	private static final String USER = "testuser123";

	private static final String PASSWORD = "Synthetic-Pass-42";

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	MutableClock clock;

	private String adminId;

	private String userId;

	@BeforeEach
	void oneAdminAndOneUser() throws Exception {
		jdbc.update("DELETE FROM password_reset_tokens");
		jdbc.update("DELETE FROM password_history");
		jdbc.update("DELETE FROM deleted_users");
		jdbc.update("DELETE FROM users");
		jdbc.update("DELETE FROM spring_session");
		register(ADMIN);
		register(USER);
		jdbc.update("UPDATE users SET role = 'ADMIN' WHERE username = ?", ADMIN);
		adminId = idOf(ADMIN);
		userId = idOf(USER);
	}

	@Test
	void anAdminSeesEveryAccountWithItsRoleStateAndCreationDate() throws Exception {
		jdbc.update("UPDATE users SET locked_until = ? WHERE username = ?",
				Timestamp.from(clock.instant().plus(Duration.ofMinutes(10))), USER);

		MvcResult result = list(loggedIn(ADMIN)).andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.length()").value(2))
			.andReturn();

		JsonNode accounts = JSON.readTree(result.getResponse().getContentAsString());
		JsonNode admin = byId(accounts, adminId);
		assertThat(admin.get("username").asString()).isEqualTo(ADMIN);
		assertThat(admin.get("email").asString()).isEqualTo(ADMIN + "@test.example.com");
		assertThat(admin.get("role").asString()).isEqualTo("ADMIN");
		assertThat(admin.get("enabled").asBoolean()).isTrue();
		assertThat(admin.get("locked").asBoolean()).isFalse();
		assertThat(Instant.parse(admin.get("createdAt").asString())).isNotNull();
		JsonNode user = byId(accounts, userId);
		assertThat(user.get("role").asString()).isEqualTo("USER");
		assertThat(user.get("locked").asBoolean()).isTrue();
	}

	@Test
	void theListCarriesOnlyTheSevenFieldsAndNeverCredentialMaterial() throws Exception {
		String body = list(loggedIn(ADMIN)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

		for (JsonNode account : JSON.readTree(body)) {
			assertThat(account.propertyNames()).containsExactlyInAnyOrder("id", "username", "email", "role",
					"enabled", "locked", "createdAt");
		}
		assertThat(body).doesNotContain("$2a$").doesNotContain("password").doesNotContain("history");
	}

	@Test
	void anExpiredLockIsNotShownAsLocked() throws Exception {
		jdbc.update("UPDATE users SET locked_until = ? WHERE username = ?",
				Timestamp.from(clock.instant().minus(Duration.ofMinutes(1))), USER);

		String body = list(loggedIn(ADMIN)).andReturn().getResponse().getContentAsString();

		assertThat(byId(JSON.readTree(body), userId).get("locked").asBoolean()).isFalse();
	}

	@Test
	void aVisitorMustLogIn() throws Exception {
		mvc.perform(get("/api/admin/users"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("authentication_required"));
	}

	@Test
	void viewingTheListIsAuditedAsAnInfoUserAdministrationEvent() throws Exception {
		Cookie session = loggedIn(ADMIN);
		LogCapture capture = LogCapture.start();

		list(session).andExpect(status().isOk());

		List<JsonNode> events = capture.awaitAudit(hasField("event.action", "user-administration"));
		assertThat(events).singleElement().satisfies((event) -> {
			assertThat(field(event, "log.level")).isEqualTo("INFO");
			assertThat(field(event, "event.outcome")).isEqualTo("success");
			assertThat(field(event, "event.type")).isEqualTo("[\"access\"]");
			assertThat(field(event, "user.id")).isEqualTo(adminId);
			assertThat(field(event, "url.path")).isEqualTo("/api/admin/users");
			assertThat(field(event, "http.request.method")).isEqualTo("GET");
			assertThat(field(event, "trace.id")).isNotBlank();
		});
		assertThat(capture.auditText()).noneMatch((line) -> line.contains(USER + "@test.example.com"));
	}

	/** Every admin path in the API contract, plus ones that do not exist; {id} is the User's own. */
	@ParameterizedTest
	@CsvSource({ "GET, /api/admin/users", "PATCH, /api/admin/users/{id}/enabled", "PATCH, /api/admin/users/{id}/role",
			"POST, /api/admin/users/{id}/unlock", "POST, /api/admin/users/{id}/require-password-change",
			"DELETE, /api/admin/users/{id}", "GET, /api/admin/users/{id}", "PUT, /api/admin/users/{id}",
			"GET, /api/admin/anything", "POST, /api/admin/users" })
	void aUserGets403OnEveryAdminPathIncludingTheirOwnAccount(String method, String path) throws Exception {
		Cookie session = loggedIn(USER);
		Csrf csrf = csrf(session);
		String target = path.replace("{id}", userId);
		LogCapture capture = LogCapture.start();

		mvc.perform(request(HttpMethod.valueOf(method), target).cookie(csrf.session())
			.header("X-CSRF-TOKEN", csrf.token())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"enabled\":false,\"role\":\"ADMIN\"}"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("access_denied"));

		List<JsonNode> events = capture.awaitAudit(hasField("event.action", "access-control"));
		assertThat(events).singleElement().satisfies((event) -> {
			assertThat(field(event, "log.level")).isEqualTo("WARN");
			assertThat(field(event, "event.outcome")).isEqualTo("failure");
			assertThat(field(event, "event.reason")).isEqualTo("access_denied");
			assertThat(field(event, "user.id")).isEqualTo(userId);
			assertThat(field(event, "url.path")).isEqualTo(target);
			assertThat(field(event, "http.request.method")).isEqualTo(method);
		});
		assertThat(capture.audit(hasField("event.action", "user-administration"))).isEmpty();
		assertThat(jdbc.queryForObject("SELECT role FROM users WHERE username = ?", String.class, USER))
			.isEqualTo("USER");
		assertThat(jdbc.queryForObject("SELECT enabled FROM users WHERE username = ?", Boolean.class, USER)).isTrue();
	}

	private static JsonNode byId(JsonNode accounts, String id) {
		for (JsonNode account : accounts) {
			if (id.equals(account.get("id").asString())) {
				return account;
			}
		}
		throw new AssertionError("No Account " + id + " in " + accounts);
	}

	private String idOf(String username) {
		return jdbc.queryForObject("SELECT id FROM users WHERE username = ?", UUID.class, username).toString();
	}

	private ResultActions list(Cookie session) throws Exception {
		return mvc.perform(get("/api/admin/users").cookie(session));
	}

	private void register(String username) throws Exception {
		Csrf csrf = csrf(null);
		mvc.perform(post("/api/register").cookie(csrf.session())
			.header("X-CSRF-TOKEN", csrf.token())
			.contentType(MediaType.APPLICATION_JSON)
			.content(JSON.writeValueAsString(
					Map.of("username", username, "email", username + "@test.example.com", "password", PASSWORD))))
			.andExpect(status().isCreated());
	}

	private Cookie loggedIn(String username) throws Exception {
		Csrf csrf = csrf(null);
		return mvc
			.perform(post("/api/login").cookie(csrf.session())
				.header("X-CSRF-TOKEN", csrf.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(JSON.writeValueAsString(Map.of("username", username, "password", PASSWORD))))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getCookie("SESSION");
	}

	record Csrf(Cookie session, String token) {
	}

	private Csrf csrf(Cookie session) throws Exception {
		MvcResult result = mvc.perform((session != null) ? get("/api/csrf").cookie(session) : get("/api/csrf"))
			.andExpect(status().isOk())
			.andReturn();
		Cookie issued = result.getResponse().getCookie("SESSION");
		String token = JsonPath.read(result.getResponse().getContentAsString(), "$.token");
		return new Csrf((issued != null) ? issued : session, token);
	}

}
