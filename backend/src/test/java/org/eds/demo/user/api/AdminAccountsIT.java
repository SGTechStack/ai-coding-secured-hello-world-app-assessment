package org.eds.demo.user.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.eds.demo.support.MutableClock;
import org.eds.demo.support.MutableClockConfiguration;
import org.eds.demo.user.domain.Role;
import org.eds.demo.user.infrastructure.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Admin creates and lists Accounts through the real admin security chain. */
@ExtendWith(OutputCaptureExtension.class)
@ActiveProfiles("local")
@AutoConfigureMockMvc
@Import(MutableClockConfiguration.class)
@SpringBootTest(
    properties = {
      "app.email.inbound.enabled=false",
      "spring.datasource.url=jdbc:h2:mem:admin-accounts-it;DB_CLOSE_DELAY=-1",
      "spring.jpa.hibernate.ddl-auto=create-drop"
    })
class AdminAccountsIT {

  private static final String ACCOUNTS_URL = "/admin/api/users";
  private static final String ADMIN_ACTOR = "root";
  private static final String CSRF_COOKIE = "XSRF-TOKEN";
  private static final String CSRF_HEADER = "X-XSRF-TOKEN";
  private static final String CSRF_TOKEN = "test-csrf-token";
  private static final Duration DEFAULT_TEMPORARY_PASSWORD_TTL = Duration.ofHours(24);

  @Autowired private MockMvc mockMvc;
  @Autowired private AppUserRepository appUserRepository;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private MutableClock clock;

  @Test
  void createReturnsTheTemporaryPasswordOnceAndStoresOnlyItsHash() throws Exception {
    clock.set(Instant.parse("2030-01-01T00:00:00Z"));

    var body =
        mockMvc
            .perform(createAccount("new-hire", Role.USER))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.username").value("new-hire"))
            .andExpect(jsonPath("$.role").value("USER"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String temporaryPassword = JsonPath.read(body, "$.temporaryPassword");

    var account = appUserRepository.findByUsername("new-hire").orElseThrow();
    assertThat(temporaryPassword).isNotBlank();
    assertThat(account.getPasswordHash()).isNotEqualTo(temporaryPassword).startsWith("{");
    assertThat(passwordEncoder.matches(temporaryPassword, account.getPasswordHash())).isTrue();
    assertThat(account.isMustChangePassword()).isTrue();
    assertThat(account.isEnabled()).isTrue();
    assertThat(account.getTempPasswordExpiresAt())
        .isEqualTo(clock.instant().plus(DEFAULT_TEMPORARY_PASSWORD_TTL));
    assertThat(account.getRoles()).containsExactly(Role.USER);
  }

  @Test
  void duplicateUsernameIsRefusedWithConflictAndLeavesTheOriginalUntouched() throws Exception {
    mockMvc.perform(createAccount("taken-name", Role.USER)).andExpect(status().isCreated());
    var originalHash =
        appUserRepository.findByUsername("taken-name").orElseThrow().getPasswordHash();

    mockMvc.perform(createAccount("taken-name", Role.ADMIN)).andExpect(status().isConflict());

    var account = appUserRepository.findByUsername("taken-name").orElseThrow();
    assertThat(account.getPasswordHash()).isEqualTo(originalHash);
    assertThat(account.getRoles()).containsExactly(Role.USER);
  }

  @Test
  void listShowsOnlyIdUsernameRoleEnabledAndCreatedDate() throws Exception {
    clock.set(Instant.parse("2031-03-04T05:06:07Z"));
    mockMvc
        .perform(createAccount("listed-manager", Role.USER_MANAGER))
        .andExpect(status().isCreated());

    var body =
        mockMvc
            .perform(get(ACCOUNTS_URL).with(user(ADMIN_ACTOR).roles("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$[?(@.username == 'listed-manager')].role")
                    .value(org.hamcrest.Matchers.contains("USER_MANAGER")))
            .andExpect(
                jsonPath("$[?(@.username == 'listed-manager')].enabled")
                    .value(org.hamcrest.Matchers.contains(true)))
            .andExpect(
                jsonPath("$[?(@.username == 'listed-manager')].createdAt")
                    .value(org.hamcrest.Matchers.contains("2031-03-04T05:06:07Z")))
            .andReturn()
            .getResponse()
            .getContentAsString();

    List<Map<String, Object>> entries = JsonPath.read(body, "$[?(@.username == 'listed-manager')]");
    assertThat(entries.get(0).keySet())
        .containsExactlyInAnyOrder("id", "username", "role", "enabled", "createdAt");
    assertThat(body)
        .doesNotContain(
            appUserRepository.findByUsername("listed-manager").orElseThrow().getPasswordHash());
  }

  @Test
  void nonAdminsAreForbiddenFromEveryAccountEndpoint() throws Exception {
    var nonAdmin = user("ada").roles("USER", "USER_MANAGER");

    mockMvc.perform(get(ACCOUNTS_URL).with(nonAdmin)).andExpect(status().isForbidden());
    mockMvc
        .perform(createAccount("sneaky", Role.ADMIN).with(nonAdmin))
        .andExpect(status().isForbidden());
    assertThat(appUserRepository.existsByUsername("sneaky")).isFalse();
  }

  @Test
  void anonymousCallersAreUnauthorizedOnEveryAccountEndpoint() throws Exception {
    mockMvc.perform(get(ACCOUNTS_URL)).andExpect(status().isUnauthorized());
    mockMvc
        .perform(
            withCsrf(
                post(ACCOUNTS_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"anon\",\"role\":\"USER\"}")))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void creationIsAuditedWithActorAndTargetButNeverThePassword(CapturedOutput output)
      throws Exception {
    var body =
        mockMvc
            .perform(createAccount("audited-hire", Role.USER))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String temporaryPassword = JsonPath.read(body, "$.temporaryPassword");

    assertThat(output.getAll())
        .contains("Account created: actor=" + ADMIN_ACTOR + ", target=audited-hire")
        .doesNotContain(temporaryPassword);
  }

  private static MockHttpServletRequestBuilder createAccount(String username, Role role) {
    return withCsrf(
        post(ACCOUNTS_URL)
            .with(user(ADMIN_ACTOR).roles("ADMIN"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"%s\",\"role\":\"%s\"}".formatted(username, role)));
  }

  private static MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder request) {
    return request.cookie(new Cookie(CSRF_COOKIE, CSRF_TOKEN)).header(CSRF_HEADER, CSRF_TOKEN);
  }
}
