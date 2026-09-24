package com.example.demo_app.admin;

import static com.example.demo_app.auth.SpaAuthFlow.fromIp;
import static com.example.demo_app.auth.SpaAuthFlow.logIn;
import static com.example.demo_app.auth.SpaAuthFlow.logInAsAdmin;
import static com.example.demo_app.auth.SpaAuthFlow.registerRequest;
import static com.example.demo_app.auth.SpaAuthFlow.registrationJson;
import static com.example.demo_app.auth.SpaAuthFlow.uniqueIp;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo_app.auth.SpaAuthFlow;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code GET /api/v1/admin/users} over the real filter chain. The shared test database always
 * holds the bootstrap {@code admin} and the seeded {@code johndoe}, plus whatever other test
 * classes registered, so assertions look for specific rows rather than an exact list.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminUserListApiTest {

  private static final String PASSWORD = "correct horse battery";
  private static final Set<String> ROW_FIELDS =
      Set.of("id", "username", "email", "firstName", "role", "enabled", "createdAt");

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper objectMapper;

  @Test
  void adminGetsEveryUserWithOnlyThePublicFields() throws Exception {
    JsonNode rows = listAsAdmin();

    assertThat(rows.isArray()).isTrue();
    for (JsonNode row : rows) {
      assertThat(fieldNames(row)).containsExactlyInAnyOrderElementsOf(ROW_FIELDS);
    }
    JsonNode admin = row(rows, "admin");
    assertThat(admin.get("id").isIntegralNumber()).isTrue();
    assertThat(admin.get("email").asText()).isEqualTo("admin@example.com");
    assertThat(admin.get("firstName").asText()).isEqualTo("Admin");
    assertThat(admin.get("role").asText()).isEqualTo("ADMIN");
    assertThat(admin.get("enabled").asBoolean()).isTrue();
    assertThat(Instant.parse(admin.get("createdAt").asText())).isNotNull();

    JsonNode john = row(rows, "johndoe");
    assertThat(john.get("email").asText()).isEqualTo("johndoe@example.com");
    assertThat(john.get("firstName").asText()).isEqualTo("John");
    assertThat(john.get("role").asText()).isEqualTo("USER");
  }

  @Test
  void neverIncludesAPasswordHash() throws Exception {
    // Field names are checked above; this catches a hash smuggled into any value.
    String body =
        mvc.perform(get("/api/v1/admin/users").session(logInAsAdmin(mvc)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(body).doesNotContain("$2a$").doesNotContain("$2b$").doesNotContain("$2y$");
  }

  @Test
  void rowsAreSortedByCreationTime() throws Exception {
    String first = register("sort-a");
    String second = register("sort-b");
    JsonNode rows = listAsAdmin();

    List<Instant> created = new ArrayList<>();
    rows.forEach(row -> created.add(Instant.parse(row.get("createdAt").asText())));
    assertThat(created).isSorted();
    assertThat(indexOf(rows, first)).isLessThan(indexOf(rows, second));
  }

  @Test
  void htmlInAFirstNameIsReturnedVerbatimAsJson() throws Exception {
    String firstName = "<img src=x onerror=alert(1)>";
    String username = "html-" + shortId();
    mvc.perform(
            registerRequest(
                    mvc, registrationJson(username, username + "@example.com", firstName, PASSWORD))
                .with(fromIp(uniqueIp())))
        .andExpect(status().isCreated());

    mvc.perform(get("/api/v1/admin/users").session(logInAsAdmin(mvc)))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(
            jsonPath("$[?(@.username == '" + username + "')].firstName").value(firstName));
  }

  @Test
  void aUserIsForbidden() throws Exception {
    mvc.perform(get("/api/v1/admin/users").session(logIn(mvc)))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.code").value("FORBIDDEN"))
        .andExpect(jsonPath("$.message").value("Forbidden"))
        .andExpect(jsonPath("$.path").value("/api/v1/admin/users"));
  }

  @Test
  void aUserIsForbiddenAnywhereUnderAdminEvenOnUnknownPaths() throws Exception {
    // The filter chain decides before any handler lookup, so no admin path leaks a 404 to a USER.
    mvc.perform(get("/api/v1/admin/anything").session(logIn(mvc)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
  }

  @Test
  void anonymousIsUnauthorized() throws Exception {
    mvc.perform(get("/api/v1/admin/users"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  private JsonNode listAsAdmin() throws Exception {
    String body =
        mvc.perform(get("/api/v1/admin/users").session(logInAsAdmin(mvc)))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(body);
  }

  private String register(String prefix) throws Exception {
    String username = prefix + "-" + shortId();
    SpaAuthFlow.register(mvc, username, PASSWORD);
    return username;
  }

  private static String shortId() {
    return UUID.randomUUID().toString().substring(0, 8);
  }

  private static JsonNode row(JsonNode rows, String username) {
    return StreamSupport.stream(rows.spliterator(), false)
        .filter(row -> row.get("username").asText().equals(username))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no row for " + username));
  }

  private static int indexOf(JsonNode rows, String username) {
    for (int i = 0; i < rows.size(); i++) {
      if (rows.get(i).get("username").asText().equals(username)) {
        return i;
      }
    }
    throw new AssertionError("no row for " + username);
  }

  private static List<String> fieldNames(JsonNode row) {
    List<String> names = new ArrayList<>();
    row.fieldNames().forEachRemaining(names::add);
    return names;
  }
}
