package org.eds.demo.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Sessions are stored in the database and can be looked up by the signed-in principal. */
@ActiveProfiles("local")
@AutoConfigureMockMvc
@SpringBootTest(
    properties = {
      "app.email.inbound.enabled=false",
      "spring.datasource.url=jdbc:h2:mem:jdbc-session-it;DB_CLOSE_DELAY=-1",
      "spring.jpa.hibernate.ddl-auto=create-drop"
    })
class JdbcSessionIT {

  @Autowired private MockMvc mockMvc;

  @Autowired private FindByIndexNameSessionRepository<? extends Session> sessions;

  @Test
  void signedInSessionCanBeFoundByPrincipalName() throws Exception {
    mockMvc
        .perform(formLogin("/login").user("alice").password("password"))
        .andExpect(status().is3xxRedirection());

    assertThat(sessions.findByPrincipalName("alice")).hasSize(1);
  }
}
