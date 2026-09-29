package com.example.securedhello;

import static com.example.securedhello.support.LogCapture.field;
import static com.example.securedhello.support.LogCapture.hasField;
import static com.example.securedhello.support.LogCapture.node;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.example.securedhello.support.LogCapture;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Registration and the Credential policy, driven through {@code POST /api/register} like the SPA.
 * All test data is synthetic.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RegistrationApiTest {

	private static final String VALID_PASSWORD = "Synthetic-Pass-42";

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@BeforeEach
	void emptyAccounts() {
		jdbc.update("DELETE FROM password_reset_tokens");
		jdbc.update("DELETE FROM password_history");
		jdbc.update("DELETE FROM users");
	}

	@Test
	void visitorRegistersAndGetsAnEnabledUserAccount() throws Exception {
		register("testuser123", "testuser123@test.example.com", VALID_PASSWORD).andExpect(status().isCreated());

		Map<String, Object> row = jdbc.queryForMap("SELECT * FROM users WHERE username = 'testuser123'");
		assertThat(row.get("email")).isEqualTo("testuser123@test.example.com");
		assertThat(row.get("role")).isEqualTo("USER");
		assertThat(row.get("enabled")).isEqualTo(true);
		assertThat(row.get("password_change_required")).isEqualTo(false);
		assertThat(row.get("failed_login_attempts")).isEqualTo(0);
		assertThat(row.get("locked_until")).isNull();
		assertThat(row.get("created_at")).isNotNull();
		assertThat(row.get("id")).isInstanceOf(UUID.class);
	}

	@Test
	void passwordIsHashedWithBcryptCost12AndRecordedAsFirstPasswordHistoryEntry() throws Exception {
		register("testuser123", "testuser123@test.example.com", VALID_PASSWORD).andExpect(status().isCreated());

		Map<String, Object> account = jdbc.queryForMap("SELECT id, password_hash FROM users");
		String hash = (String) account.get("password_hash");
		assertThat(hash).startsWith("$2").contains("$12$").doesNotContain(VALID_PASSWORD);
		assertThat(new BCryptPasswordEncoder().matches(VALID_PASSWORD, hash)).isTrue();

		List<Map<String, Object>> history = jdbc.queryForList("SELECT user_id, password_hash FROM password_history");
		assertThat(history).hasSize(1);
		assertThat(history.get(0).get("user_id")).isEqualTo(account.get("id"));
		assertThat(history.get(0).get("password_hash")).isEqualTo(hash);
	}

	@Test
	void roleInTheBodyIsIgnored() throws Exception {
		String body = """
				{"username":"testuser123","email":"testuser123@test.example.com","password":"%s","role":"ADMIN"}
				""".formatted(VALID_PASSWORD);

		registerRaw(body).andExpect(status().isCreated());

		assertThat(jdbc.queryForObject("SELECT role FROM users", String.class)).isEqualTo("USER");
	}

	@Test
	void usernameAndEmailAreStoredInLowercase() throws Exception {
		register("TestUser123", "TestUser123@Test.Example.COM", VALID_PASSWORD).andExpect(status().isCreated());

		Map<String, Object> row = jdbc.queryForMap("SELECT username, email FROM users");
		assertThat(row.get("username")).isEqualTo("testuser123");
		assertThat(row.get("email")).isEqualTo("testuser123@test.example.com");
	}

	@ParameterizedTest
	@CsvSource({ "Alice123, other@test.example.com", "someoneelse, ALICE@TEST.EXAMPLE.COM",
			"alice123, alice@test.example.com" })
	void clashOnUsernameOrEmailIsOneCaseInsensitiveUserExistError(String username, String email) throws Exception {
		register("alice123", "alice@test.example.com", VALID_PASSWORD).andExpect(status().isCreated());

		register(username, email, VALID_PASSWORD).andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("user_exist"))
			.andExpect(jsonPath("$.detail").value("user exist"))
			.andExpect(jsonPath("$.fields").doesNotExist())
			.andExpect(jsonPath("$.violations").doesNotExist());

		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class)).isEqualTo(1);
	}

	@ParameterizedTest
	@CsvSource({ "lowercase-only-password-1, uppercase", "UPPERCASE-ONLY-PASSWORD-1, lowercase",
			"No-Digits-In-This-Password, digit", "NoSpecialCharacters42, special" })
	void eachCharacterClassRuleIsReportedOnItsOwn(String password, String violation) throws Exception {
		register("testuser123", "testuser123@test.example.com", password).andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("password_policy"))
			.andExpect(jsonPath("$.violations", contains(violation)));

		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class)).isZero();
	}

	@Test
	void passwordShorterThan12CharactersIsRejected() throws Exception {
		register("testuser123", "testuser123@test.example.com", "Short-Pw-42").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("password_policy"))
			.andExpect(jsonPath("$.violations", contains("min_length")));
	}

	@Test
	void everyBrokenRuleIsListed() throws Exception {
		register("testuser123", "testuser123@test.example.com", "abc").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("password_policy"))
			.andExpect(jsonPath("$.violations", containsInAnyOrder("min_length", "uppercase", "digit", "special")));
	}

	@ParameterizedTest
	@ValueSource(strings = { "Twelve Chars 1", "Pass!word1234", "Unicode€Sign12" })
	void anyPrintableNonLetterOrDigitCountsAsSpecialIncludingSpaces(String password) throws Exception {
		register("testuser123", "testuser123@test.example.com", password).andExpect(status().isCreated());
	}

	@Test
	void controlCharactersDoNotCountAsSpecial() throws Exception {
		register("testuser123", "testuser123@test.example.com", "Abcdefghijk1\u0007\u200b")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.violations", contains("special")));
	}

	@Test
	void unicodeLettersAndDigitsCountAsLettersAndDigits() throws Exception {
		register("testuser123", "testuser123@test.example.com", "Äbcdéfghij-٣٤").andExpect(status().isCreated());
	}

	@Test
	void passwordOf64CharactersIsAcceptedAnd65IsRejected() throws Exception {
		String sixtyFour = "Aa1!" + "x".repeat(60);
		register("testuser123", "testuser123@test.example.com", sixtyFour).andExpect(status().isCreated());

		register("testuser456", "testuser456@test.example.com", sixtyFour + "x").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("password_policy"))
			.andExpect(jsonPath("$.violations", contains("max_length")));
	}

	@Test
	void passwordOver72Utf8BytesIsRejectedEvenUnder64Characters() throws Exception {
		// 30 characters of 3 bytes each: 90 bytes, well under 64 characters.
		String multiByte = "Aa1!" + "€".repeat(30);
		register("testuser123", "testuser123@test.example.com", multiByte).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("password_policy"))
			.andExpect(jsonPath("$.violations", contains("max_bytes")));

		// 72 bytes exactly is accepted.
		String seventyTwoBytes = "Aa1!" + "€".repeat(22) + "xx";
		register("testuser123", "testuser123@test.example.com", seventyTwoBytes).andExpect(status().isCreated());
	}

	@ParameterizedTest
	@ValueSource(strings = { "Password123!", "pASSWORD123!", "P@ssw0rd1234" })
	void commonPasswordIsRejectedCaseInsensitively(String password) throws Exception {
		register("testuser123", "testuser123@test.example.com", password).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("password_policy"))
			.andExpect(jsonPath("$.violations", contains("common_password")));
	}

	@Test
	void shortCommonPasswordReportsTheCommonPasswordRuleToo() throws Exception {
		register("testuser123", "testuser123@test.example.com", "password").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.violations",
					containsInAnyOrder("min_length", "uppercase", "digit", "special", "common_password")));
	}

	@ParameterizedTest
	@ValueSource(strings = { "ab", "a23456789012345678901234567890123", "test_user", "test user", "tëstuser", "" })
	void usernameMustBe3To32LettersAndDigits(String username) throws Exception {
		register(username, "testuser123@test.example.com", VALID_PASSWORD).andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("validation"))
			.andExpect(jsonPath("$.fields", contains("username")));
	}

	@ParameterizedTest
	@ValueSource(strings = { "abc", "a2345678901234567890123456789012" })
	void usernameOf3And32CharactersIsAccepted(String username) throws Exception {
		register(username, "testuser123@test.example.com", VALID_PASSWORD).andExpect(status().isCreated());
	}

	@ParameterizedTest
	@ValueSource(strings = { "not-an-email", "missing-domain@", "@test.example.com", "two@@test.example.com",
			"spaces in@test.example.com", "" })
	void malformedEmailIsRejected(String email) throws Exception {
		register("testuser123", email, VALID_PASSWORD).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("validation"))
			.andExpect(jsonPath("$.fields", contains("email")));
	}

	@Test
	void emailOf254CharactersIsAcceptedAnd255IsRejected() throws Exception {
		register("testuser123", emailOfLength(254), VALID_PASSWORD).andExpect(status().isCreated());

		register("testuser456", emailOfLength(255), VALID_PASSWORD).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("validation"))
			.andExpect(jsonPath("$.fields", contains("email")));
	}

	@Test
	void missingFieldsAreValidationErrorsNamingEachField() throws Exception {
		registerRaw("{}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("validation"))
			.andExpect(jsonPath("$.fields", containsInAnyOrder("username", "email", "password")));
	}

	@Test
	void malformedJsonIsAValidationError() throws Exception {
		registerRaw("{\"username\":").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("validation"));
	}

	@Test
	void inputChecksRunBeforeThePasswordPolicy() throws Exception {
		register("x", "testuser123@test.example.com", "weak").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("validation"))
			.andExpect(jsonPath("$.fields", contains("username")));
	}

	@Test
	void registerRequiresACsrfToken() throws Exception {
		mvc.perform(post("/api/register").contentType(MediaType.APPLICATION_JSON)
			.content(body("testuser123", "testuser123@test.example.com", VALID_PASSWORD)))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("csrf_invalid"));
	}

	@Test
	void registrationEmitsAUserProvisioningAuditEventKeyedByTheNewAccountUuid() throws Exception {
		LogCapture capture = LogCapture.start();

		register("testuser123", "testuser123@test.example.com", VALID_PASSWORD).andExpect(status().isCreated());

		String id = jdbc.queryForObject("SELECT id FROM users", UUID.class).toString();
		JsonNode event = capture.awaitAudit(hasField("event.action", "user-provisioning")).get(0);
		assertThat(field(event, "log.level")).isEqualTo("INFO");
		assertThat(field(event, "event.outcome")).isEqualTo("success");
		assertThat(field(event, "user.id")).isEqualTo(id);
		assertThat(field(event, "url.path")).isEqualTo("/api/register");
		assertThat(field(event, "http.request.method")).isEqualTo("POST");
		assertThat(field(event, "trace.id")).isNotBlank();
		assertNoPersonalDataOrSecrets(capture);
	}

	@Test
	void validationFailureEmitsAWarnAccessControlEventNamingOnlyTheFailingFields() throws Exception {
		LogCapture capture = LogCapture.start();

		register("x", "not-an-email", VALID_PASSWORD).andExpect(status().isBadRequest());

		JsonNode event = capture.awaitAudit(hasField("event.action", "access-control")).get(0);
		assertThat(field(event, "log.level")).isEqualTo("WARN");
		assertThat(field(event, "event.outcome")).isEqualTo("failure");
		assertThat(field(event, "event.reason")).isEqualTo("validation");
		assertThat(node(event, "validation.fields").toString()).isEqualTo("[\"email\",\"username\"]");
		assertThat(field(event, "url.path")).isEqualTo("/api/register");
		assertThat(capture.audit(hasField("event.action", "access-control"))).hasSize(1);
		assertThat(String.join("\n", capture.auditText())).doesNotContain("not-an-email");
	}

	@Test
	void passwordPolicyFailureEmitsAWarnAccessControlEventNamingOnlyThePasswordField() throws Exception {
		LogCapture capture = LogCapture.start();

		register("testuser123", "testuser123@test.example.com", "weak").andExpect(status().isBadRequest());

		JsonNode event = capture.awaitAudit(hasField("event.action", "access-control")).get(0);
		assertThat(field(event, "log.level")).isEqualTo("WARN");
		assertThat(field(event, "event.reason")).isEqualTo("password_policy");
		assertThat(node(event, "validation.fields").toString()).isEqualTo("[\"password\"]");
		assertNoPersonalDataOrSecrets(capture);
	}

	@Test
	void userExistEmitsAWarnUserProvisioningFailure() throws Exception {
		register("testuser123", "testuser123@test.example.com", VALID_PASSWORD).andExpect(status().isCreated());
		LogCapture capture = LogCapture.start();

		register("testuser123", "testuser123@test.example.com", VALID_PASSWORD).andExpect(status().isBadRequest());

		JsonNode event = capture.awaitAudit(hasField("event.action", "user-provisioning")).get(0);
		assertThat(field(event, "log.level")).isEqualTo("WARN");
		assertThat(field(event, "event.outcome")).isEqualTo("failure");
		assertThat(field(event, "event.reason")).isEqualTo("user_exist");
		assertThat(node(event, "user.id")).isNull();
		assertNoPersonalDataOrSecrets(capture);
	}

	/** A well-formed email of the given length: a 64-character local part and 63-character labels. */
	private static String emailOfLength(int length) {
		String prefix = "a".repeat(64) + "@" + "b".repeat(63) + "." + "c".repeat(63) + ".";
		String email = prefix + "d".repeat(length - prefix.length() - ".example".length()) + ".example";
		assertThat(email).hasSize(length);
		return email;
	}

	private static void assertNoPersonalDataOrSecrets(LogCapture capture) {
		String logs = String.join("\n", capture.auditText()) + String.join("\n", capture.applicationText());
		assertThat(logs).doesNotContain("testuser123").doesNotContain(VALID_PASSWORD).doesNotContain("weak\"");
	}

	private ResultActions register(String username, String email, String password) throws Exception {
		return registerRaw(body(username, email, password));
	}

	private static String body(String username, String email, String password) {
		return JSON.writeValueAsString(Map.of("username", username, "email", email, "password", password));
	}

	private ResultActions registerRaw(String json) throws Exception {
		MvcResult csrf = mvc.perform(get("/api/csrf")).andExpect(status().isOk()).andReturn();
		Cookie session = csrf.getResponse().getCookie("SESSION");
		String token = JsonPath.read(csrf.getResponse().getContentAsString(), "$.token");
		return mvc.perform(post("/api/register").cookie(session)
			.header("X-CSRF-TOKEN", token)
			.contentType(MediaType.APPLICATION_JSON)
			.content(json));
	}

}
