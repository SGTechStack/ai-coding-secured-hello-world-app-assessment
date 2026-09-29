package com.example.securedhello.account;

import static com.example.securedhello.support.LogCapture.field;
import static com.example.securedhello.support.LogCapture.hasField;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.example.securedhello.support.LogCapture;

import tools.jackson.databind.JsonNode;

/**
 * A registration that loses a race: another registration takes the username or email between the
 * clash check and the insert, so the database's unique constraint rejects it. The existence check
 * is stubbed to simulate the race, which cannot be timed reliably through the API alone.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ConcurrentRegistrationApiTest {

	private static final String USERNAME = "racinguser123";

	private static final String EMAIL = "racinguser123@test.example.com";

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@MockitoSpyBean
	AccountRepository accounts;

	@BeforeEach
	void emptyAccounts() {
		jdbc.update("DELETE FROM password_reset_tokens");
		jdbc.update("DELETE FROM password_history");
		jdbc.update("DELETE FROM users");
	}

	@Test
	void uniqueConstraintViolationIsAUserExistErrorAndLeaksNoPersonalData() throws Exception {
		register().andExpect(status().isCreated());
		doReturn(false).when(accounts).existsByUsernameOrEmail(anyString(), anyString());
		LogCapture capture = LogCapture.start();

		register().andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("user_exist"))
			.andExpect(jsonPath("$.detail").value("user exist"));

		JsonNode event = capture.awaitAudit(hasField("event.action", "user-provisioning")).get(0);
		assertThat(field(event, "event.reason")).isEqualTo("user_exist");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class)).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM password_history", Integer.class)).isEqualTo(1);
		String logs = String.join("\n", capture.applicationText()) + String.join("\n", capture.auditText());
		assertThat(logs).doesNotContainIgnoringCase(USERNAME).doesNotContainIgnoringCase("test.example.com");
	}

	private ResultActions register() throws Exception {
		MvcResult csrf = mvc.perform(get("/api/csrf")).andExpect(status().isOk()).andReturn();
		Cookie session = csrf.getResponse().getCookie("SESSION");
		String token = JsonPath.read(csrf.getResponse().getContentAsString(), "$.token");
		return mvc.perform(post("/api/register").cookie(session)
			.header("X-CSRF-TOKEN", token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"username":"%s","email":"%s","password":"Synthetic-Pass-42"}
					""".formatted(USERNAME, EMAIL)));
	}

}
