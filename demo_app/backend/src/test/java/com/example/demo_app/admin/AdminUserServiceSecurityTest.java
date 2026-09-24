package com.example.demo_app.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.demo_app.audit.Actor;
import com.example.demo_app.user.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

/**
 * Defence in depth: the admin service refuses non-admins on its own, not only because the filter
 * chain guards {@code /api/v1/admin/**}. The HTTP seam can't show this (the chain answers first),
 * so these tests call the service directly.
 */
@SpringBootTest
@AutoConfigureMockMvc // unused here; it keeps this class on the other API tests' cached context
@ActiveProfiles("test")
class AdminUserServiceSecurityTest {

  @Autowired private AdminUserService service;

  @Test
  @WithMockUser(roles = "ADMIN")
  void anAdminMayListUsers() {
    assertThat(service.listUsers()).extracting(AdminUserView::username).contains("admin");
  }

  @Test
  @WithMockUser(roles = "USER")
  void aUserIsDenied() {
    assertThatThrownBy(service::listUsers).isInstanceOf(AccessDeniedException.class);
  }

  @Test
  @WithMockUser(roles = "USER")
  void aUserIsDeniedEveryAccountAction() {
    // Refused before the method runs, so the made-up actor and unknown id never matter.
    Actor actor = new Actor("user", "203.0.113.1");
    assertThatThrownBy(() -> service.setEnabled(1L, false, actor))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> service.setRole(1L, Role.ADMIN, actor))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> service.deleteUser(1L, actor))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void noAuthenticationIsDenied() {
    assertThatThrownBy(service::listUsers)
        .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
  }
}
