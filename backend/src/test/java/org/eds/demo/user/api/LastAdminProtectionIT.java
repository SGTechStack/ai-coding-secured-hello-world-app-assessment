package org.eds.demo.user.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.Set;
import org.eds.demo.user.domain.AppUser;
import org.eds.demo.user.domain.Role;
import org.eds.demo.user.infrastructure.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** The last enabled ADMIN cannot be disabled, demoted or deleted, so admin access is never lost. */
@ActiveProfiles("local")
@AutoConfigureMockMvc
@SpringBootTest(
    properties = {
      "app.email.inbound.enabled=false",
      "spring.datasource.url=jdbc:h2:mem:last-admin-it;DB_CLOSE_DELAY=-1",
      "spring.jpa.hibernate.ddl-auto=create-drop"
    })
class LastAdminProtectionIT {

  private static final String USERS_URL = "/admin/api/users/";
  private static final String ACTOR = "acting-admin";
  private static final String CSRF_COOKIE = "XSRF-TOKEN";
  private static final String CSRF_HEADER = "X-XSRF-TOKEN";
  private static final String CSRF_TOKEN = "test-csrf-token";
  private static final String REFUSAL = "You cannot %s the last remaining enabled administrator";

  @Autowired private MockMvc mockMvc;
  @Autowired private AppUserRepository appUserRepository;

  @BeforeEach
  void startWithNoAccounts() {
    appUserRepository.deleteAll();
  }

  @Test
  void lastEnabledAdminCannotBeDisabled() throws Exception {
    var onlyAdmin = save("only-admin", Role.ADMIN, true);

    mockMvc
        .perform(update(onlyAdmin, "enabled", "{\"enabled\":false}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value(REFUSAL.formatted("disable")));

    assertThat(reload(onlyAdmin).isEnabled()).isTrue();
  }

  @Test
  void lastEnabledAdminCannotBeDemoted() throws Exception {
    var onlyAdmin = save("only-admin", Role.ADMIN, true);

    mockMvc
        .perform(update(onlyAdmin, "role", "{\"role\":\"USER\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value(REFUSAL.formatted("demote")));

    assertThat(reload(onlyAdmin).getRoles()).containsExactly(Role.ADMIN);
  }

  @Test
  void lastEnabledAdminCannotBeDeleted() throws Exception {
    var onlyAdmin = save("only-admin", Role.ADMIN, true);

    mockMvc
        .perform(asAdmin(delete(USERS_URL + onlyAdmin.getId().value())))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value(REFUSAL.formatted("delete")));

    assertThat(appUserRepository.findByUsername("only-admin")).isPresent();
  }

  @Test
  void anAdminCanBeDisabledWhileAnotherEnabledAdminRemains() throws Exception {
    var first = save("first-admin", Role.ADMIN, true);
    save("second-admin", Role.ADMIN, true);

    mockMvc
        .perform(update(first, "enabled", "{\"enabled\":false}"))
        .andExpect(status().isNoContent());

    assertThat(reload(first).isEnabled()).isFalse();
  }

  @Test
  void aDisabledAdminDoesNotCountAsRemainingSoTheEnabledOneIsProtected() throws Exception {
    save("disabled-admin", Role.ADMIN, false);
    var enabledAdmin = save("enabled-admin", Role.ADMIN, true);

    mockMvc
        .perform(update(enabledAdmin, "role", "{\"role\":\"USER\"}"))
        .andExpect(status().isConflict());
  }

  @Test
  void aDisabledAdminCanBeDeletedEvenWhenItIsTheOnlyAdminRecord() throws Exception {
    var disabled = save("disabled-admin", Role.ADMIN, false);

    mockMvc
        .perform(asAdmin(delete(USERS_URL + disabled.getId().value())))
        .andExpect(status().isNoContent());
  }

  private AppUser save(String username, Role role, boolean enabled) {
    var account = AppUser.create(username, Set.of(role));
    if (!enabled) {
      account.disable();
    }
    return appUserRepository.saveAndFlush(account);
  }

  private AppUser reload(AppUser account) {
    return appUserRepository.findByUsername(account.getUsername()).orElseThrow();
  }

  private static MockHttpServletRequestBuilder update(AppUser target, String action, String json) {
    return asAdmin(put(USERS_URL + target.getId().value() + "/" + action))
        .contentType(MediaType.APPLICATION_JSON)
        .content(json);
  }

  private static MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder request) {
    return request
        .with(user(ACTOR).roles("ADMIN"))
        .cookie(new Cookie(CSRF_COOKIE, CSRF_TOKEN))
        .header(CSRF_HEADER, CSRF_TOKEN);
  }
}
