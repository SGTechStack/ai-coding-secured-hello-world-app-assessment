package org.eds.demo.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
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
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "app.admin.username=session-admin",
      "app.admin.password=test-only-session-secret"
    })
class JdbcSessionIT {

  @Autowired private MockMvc mockMvc;

  @Autowired private FindByIndexNameSessionRepository<? extends Session> sessions;

  @Test
  void signedInSessionCanBeFoundByPrincipalName() throws Exception {
    mockMvc
        .perform(
            post("/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"username\":\"session-admin\",\"password\":\"test-only-session-secret\"}")
                .cookie(new Cookie("XSRF-TOKEN", "test-csrf-token"))
                .header("X-XSRF-TOKEN", "test-csrf-token"))
        .andExpect(status().isOk());

    assertThat(sessions.findByPrincipalName("session-admin")).hasSize(1);
  }
}
