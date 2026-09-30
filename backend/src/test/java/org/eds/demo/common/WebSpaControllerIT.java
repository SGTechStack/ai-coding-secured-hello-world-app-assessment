package org.eds.demo.common;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
class WebSpaControllerIT {

  @Autowired private MockMvc mockMvc;

  @Test
  void rootRedirectsUnauthenticatedUserToWelcome() throws Exception {
    mockMvc
        .perform(get("/"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/welcome"));
  }

  @Test
  void rootRedirectsAuthenticatedUserToApp() throws Exception {
    mockMvc
        .perform(get("/").with(user("ada").roles("USER")))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/app/"));
  }

  @Test
  void appRouteForbidsRolelessUser() throws Exception {
    mockMvc
        .perform(get("/app/dashboard").with(user("ada").authorities()))
        .andExpect(status().isForbidden());
  }

  @Test
  void welcomeForwardsToWelcomeIndexHtml() throws Exception {
    mockMvc
        .perform(get("/welcome"))
        .andExpect(status().isOk())
        .andExpect(forwardedUrl("/welcome/index.html"));
  }

  @Test
  void welcomeResponseIncludesContentSecurityPolicyHeader() throws Exception {
    mockMvc
        .perform(get("/welcome"))
        .andExpect(status().isOk())
        .andExpect(
            header().string("Content-Security-Policy", containsString("worker-src 'self' blob:;")));
  }

  @Test
  void spaRouteWithoutExtensionForwardsToIndexHtml() throws Exception {
    mockMvc
        .perform(get("/app/dashboard").with(user("ada").roles("USER")))
        .andExpect(status().isOk())
        .andExpect(forwardedUrl("/index.html"));
  }

  @Test
  void spaRouteWithExtensionForwardsToAsset() throws Exception {
    mockMvc
        .perform(get("/app/assets/main.js").with(user("ada").roles("USER")))
        .andExpect(status().isOk())
        .andExpect(forwardedUrl("/assets/main.js"));
  }

  @Test
  void spaRootForwardsToIndexHtml() throws Exception {
    mockMvc
        .perform(get("/app").with(user("ada").roles("USER")))
        .andExpect(status().isOk())
        .andExpect(forwardedUrl("/index.html"));
  }
}
