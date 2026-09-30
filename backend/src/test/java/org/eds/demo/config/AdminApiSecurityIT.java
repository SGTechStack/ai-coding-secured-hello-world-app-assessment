package org.eds.demo.config;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** {@code /admin/api/**} admits only {@code ROLE_ADMIN}; the path need not map to a controller. */
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
class AdminApiSecurityIT {

  private static final String ADMIN_PROBE_URL = "/admin/api/v1/probe";

  @Autowired private MockMvc mockMvc;

  @Test
  void anonymousIsUnauthorized() throws Exception {
    mockMvc.perform(get(ADMIN_PROBE_URL)).andExpect(status().isUnauthorized());
  }

  @Test
  void nonAdminRoleIsForbidden() throws Exception {
    mockMvc
        .perform(get(ADMIN_PROBE_URL).with(user("ada").roles("USER")))
        .andExpect(status().isForbidden());
  }

  @Test
  void adminPassesSecurity() throws Exception {
    // No controller is mapped, so passing the security chain surfaces as 404, not 401/403.
    mockMvc
        .perform(get(ADMIN_PROBE_URL).with(user("root").roles("ADMIN")))
        .andExpect(status().isNotFound());
  }
}
