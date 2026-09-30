package org.eds.demo.user.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.Set;
import java.util.UUID;
import org.eds.demo.user.domain.AppUser;
import org.eds.demo.user.domain.Role;
import org.eds.demo.user.infrastructure.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Admin enables, disables, changes Role and deletes Accounts through the real admin chain. */
@ExtendWith(OutputCaptureExtension.class)
@ActiveProfiles("local")
@AutoConfigureMockMvc
@SpringBootTest(
    properties = {
      "app.email.inbound.enabled=false",
      "spring.datasource.url=jdbc:h2:mem:admin-lifecycle-it;DB_CLOSE_DELAY=-1",
      "spring.jpa.hibernate.ddl-auto=create-drop"
    })
class AdminAccountLifecycleIT {

  private static final String USERS_URL = "/admin/api/users/";
  private static final String ADMIN_ACTOR = "lifecycle-admin";
  private static final String PASSWORD = "test-only-lifecycle-secret";
  private static final String SESSION_COOKIE = "SESSION";
  private static final String CSRF_COOKIE = "XSRF-TOKEN";
  private static final String CSRF_HEADER = "X-XSRF-TOKEN";
  private static final String CSRF_TOKEN = "test-csrf-token";

  @Autowired private MockMvc mockMvc;
  @Autowired private AppUserRepository appUserRepository;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private FindByIndexNameSessionRepository<? extends Session> sessions;

  @Test
  void disablingEndsTheAccountsSessionsAndRefusesFurtherSignIn() throws Exception {
    var target = account("to-disable", Role.USER);
    var session = signIn("to-disable");
    mockMvc.perform(get("/api/hello").cookie(session)).andExpect(status().isOk());

    mockMvc.perform(setEnabled(target, false)).andExpect(status().isNoContent());

    assertThat(appUserRepository.findByUsername("to-disable").orElseThrow().isEnabled()).isFalse();
    assertThat(sessions.findByPrincipalName("to-disable")).isEmpty();
    mockMvc.perform(get("/api/hello").cookie(session)).andExpect(status().isUnauthorized());
    mockMvc.perform(signInRequest("to-disable")).andExpect(status().isUnauthorized());
  }

  @Test
  void adminCannotDisableTheirOwnAccount() throws Exception {
    var self = account(ADMIN_ACTOR, Role.ADMIN);

    mockMvc
        .perform(setEnabled(self, false))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value("You cannot disable your own account"));

    assertThat(appUserRepository.findByUsername(ADMIN_ACTOR).orElseThrow().isEnabled()).isTrue();
  }

  @Test
  void enablingRestoresSignInAndBothChangesAreAudited(CapturedOutput output) throws Exception {
    var target = account("to-toggle", Role.USER);

    mockMvc.perform(setEnabled(target, false)).andExpect(status().isNoContent());
    mockMvc.perform(setEnabled(target, true)).andExpect(status().isNoContent());

    signIn("to-toggle");
    assertThat(output.getAll())
        .contains("Account disabled: actor=" + ADMIN_ACTOR + ", target=to-toggle")
        .contains("Account enabled: actor=" + ADMIN_ACTOR + ", target=to-toggle");
  }

  @Test
  void changingRoleReplacesTheRoleAndIsAudited(CapturedOutput output) throws Exception {
    var target = account("to-promote", Role.USER);

    mockMvc.perform(setRole(target, "USER_MANAGER")).andExpect(status().isNoContent());

    assertThat(appUserRepository.findByUsername("to-promote").orElseThrow().getRoles())
        .containsExactly(Role.USER_MANAGER);
    assertThat(output.getAll())
        .contains(
            "Account role changed: actor="
                + ADMIN_ACTOR
                + ", target=to-promote, from=USER, to=USER_MANAGER");
  }

  @Test
  void adminCannotDemoteTheirOwnAccount() throws Exception {
    var self = account(ADMIN_ACTOR, Role.ADMIN);

    mockMvc
        .perform(setRole(self, "USER"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value("You cannot demote your own account"));

    assertThat(appUserRepository.findByUsername(ADMIN_ACTOR).orElseThrow().getRoles())
        .containsExactly(Role.ADMIN);
  }

  @Test
  void changingRoleEndsTheAccountsSessionsSoTheOldAuthoritiesDoNotLinger() throws Exception {
    var target = account("to-demote", Role.USER_MANAGER);
    var session = signIn("to-demote");

    mockMvc.perform(setRole(target, "USER")).andExpect(status().isNoContent());

    mockMvc.perform(get("/api/hello").cookie(session)).andExpect(status().isUnauthorized());
  }

  @Test
  void unknownRoleIsRejectedAndLeavesTheRoleUnchanged() throws Exception {
    var target = account("bad-role-target", Role.USER);

    mockMvc.perform(setRole(target, "SUPERUSER")).andExpect(status().isBadRequest());

    assertThat(appUserRepository.findByUsername("bad-role-target").orElseThrow().getRoles())
        .containsExactly(Role.USER);
  }

  @Test
  void deletingRemovesTheAccountItsRolesAndItsSessionsAndIsAudited(CapturedOutput output)
      throws Exception {
    var target = account("to-delete", Role.USER_MANAGER);
    var session = signIn("to-delete");

    mockMvc.perform(deleteAccount(target.getId().value())).andExpect(status().isNoContent());

    assertThat(appUserRepository.existsByUsername("to-delete")).isFalse();
    assertThat(appUserRepository.existsById(target.getId().value())).isFalse();
    assertThat(sessions.findByPrincipalName("to-delete")).isEmpty();
    mockMvc.perform(get("/api/hello").cookie(session)).andExpect(status().isUnauthorized());
    mockMvc.perform(signInRequest("to-delete")).andExpect(status().isUnauthorized());
    assertThat(output.getAll())
        .contains("Account deleted: actor=" + ADMIN_ACTOR + ", target=to-delete");
  }

  @Test
  void adminCannotDeleteTheirOwnAccount() throws Exception {
    var self = account(ADMIN_ACTOR, Role.ADMIN);

    mockMvc
        .perform(deleteAccount(self.getId().value()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value("You cannot delete your own account"));

    assertThat(appUserRepository.existsByUsername(ADMIN_ACTOR)).isTrue();
  }

  @Test
  void nonAdminsAreForbiddenAndAnonymousCallersUnauthorizedOnEveryAction() throws Exception {
    var target = account("untouchable", Role.USER);
    var id = target.getId().value();
    var nonAdmin = user("ada").roles("USER", "USER_MANAGER");

    for (var request :
        java.util.List.of(
            put(USERS_URL + id + "/enabled")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":false}"),
            put(USERS_URL + id + "/role")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"ADMIN\"}"),
            delete(USERS_URL + id))) {
      // Anonymous first: with() mutates the builder, so the non-admin call must come last.
      mockMvc.perform(withCsrf(request)).andExpect(status().isUnauthorized());
      mockMvc.perform(request.with(nonAdmin)).andExpect(status().isForbidden());
    }

    var untouched = appUserRepository.findByUsername("untouchable").orElseThrow();
    assertThat(untouched.isEnabled()).isTrue();
    assertThat(untouched.getRoles()).containsExactly(Role.USER);
  }

  @Test
  void unknownAccountIdIsNotFoundForEveryAction() throws Exception {
    var unknown = UUID.randomUUID();

    mockMvc
        .perform(
            asAdmin(put(USERS_URL + unknown + "/enabled"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":false}"))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            asAdmin(put(USERS_URL + unknown + "/role"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"USER\"}"))
        .andExpect(status().isNotFound());
    mockMvc.perform(deleteAccount(unknown)).andExpect(status().isNotFound());
  }

  private AppUser account(String username, Role role) {
    var existing = appUserRepository.findByUsername(username);
    if (existing.isPresent()) {
      return existing.get();
    }
    var account = AppUser.create(username, Set.of(role));
    account.updatePasswordHash(passwordEncoder.encode(PASSWORD));
    return appUserRepository.saveAndFlush(account);
  }

  private Cookie signIn(String username) throws Exception {
    return mockMvc
        .perform(signInRequest(username))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getCookie(SESSION_COOKIE);
  }

  private static MockHttpServletRequestBuilder signInRequest(String username) {
    return withCsrf(
        post("/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, PASSWORD)));
  }

  private static MockHttpServletRequestBuilder setEnabled(AppUser target, boolean enabled) {
    return asAdmin(put(USERS_URL + target.getId().value() + "/enabled"))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"enabled\":%s}".formatted(enabled));
  }

  private static MockHttpServletRequestBuilder setRole(AppUser target, String role) {
    return asAdmin(put(USERS_URL + target.getId().value() + "/role"))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"role\":\"%s\"}".formatted(role));
  }

  private static MockHttpServletRequestBuilder deleteAccount(UUID id) {
    return asAdmin(delete(USERS_URL + id));
  }

  private static MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder request) {
    return withCsrf(request.with(user(ADMIN_ACTOR).roles("ADMIN")));
  }

  private static MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder request) {
    return request.cookie(new Cookie(CSRF_COOKIE, CSRF_TOKEN)).header(CSRF_HEADER, CSRF_TOKEN);
  }
}
