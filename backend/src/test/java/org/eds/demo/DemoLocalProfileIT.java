package org.eds.demo;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("local")
@AutoConfigureMockMvc
// app.email.inbound.queue-name in application-local.properties points at a real per-developer
// AWS SQS queue; disable the listener here so this context-load smoke test doesn't depend on AWS
// credentials/network access (unavailable in CI).
@SpringBootTest(
    properties = {"app.email.inbound.enabled=false", "spring.datasource.url=jdbc:h2:mem:localit"})
class DemoLocalProfileIT {

  @Autowired private MockMvc mockMvc;

  @Test
  void localProfileHandlesWebRequests() throws Exception {
    mockMvc.perform(get("/")).andExpect(status().is3xxRedirection());
  }

  @Test
  void localProfileExposesOpenApiDocument() throws Exception {
    mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
  }

  @Test
  void localProfileEnforcesCsrfOnApiChain() throws Exception {
    mockMvc.perform(post("/api/v1/me/password")).andExpect(status().isForbidden());
  }

  @Test
  void localProfileDoesNotAcceptHttpBasic() throws Exception {
    mockMvc
        .perform(get("/api/hello").header("Authorization", "Basic YWRtaW46YWRtaW4="))
        .andExpect(status().isUnauthorized());
  }
}
