package com.example.demo_app.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
  void noAuthenticationIsDenied() {
    assertThatThrownBy(service::listUsers)
        .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
  }
}
