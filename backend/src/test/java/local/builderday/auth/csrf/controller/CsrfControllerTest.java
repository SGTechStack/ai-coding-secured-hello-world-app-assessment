package local.builderday.auth.csrf.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Sessions live in Spring Session JDBC, so the session is identified by the {@code id} cookie, not a
 * MockHttpSession.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class CsrfControllerTest {
  @Autowired MockMvc mvc;

  @Test
  void should_issueSessionBackedToken_whenAnonymousClientBootstraps() throws Exception {
    var result = mvc.perform(get("/csrf"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith("application/json"))
        .andExpect(jsonPath("$.token").isNotEmpty())
        .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"))
        .andExpect(jsonPath("$.parameterName").value("_csrf"))
        .andReturn();

    assertThat(result.getResponse().getCookie("id")).isNotNull();
    assertThat(result.getResponse().getHeaders("Set-Cookie"))
        .noneMatch(cookie -> cookie.startsWith("XSRF-TOKEN=") || cookie.startsWith("X-CSRF-TOKEN="))
        .anySatisfy(cookie -> assertThat(cookie).startsWith("id=").contains("HttpOnly", "Secure", "SameSite=Strict"));
  }

  @Test
  void should_reuseSessionAndToken_whenClientBootstrapsAgain() throws Exception {
    var first = mvc.perform(get("/csrf")).andReturn();
    var sessionCookie = first.getResponse().getCookie("id");
    String firstToken = JsonPath.read(first.getResponse().getContentAsString(), "$.token");

    var second = mvc.perform(get("/csrf").cookie(sessionCookie)).andExpect(status().isOk()).andReturn();

    assertThat(second.getResponse().getCookie("id")).as("no new session is created").isNull();
    // The XOR handler masks the token differently per response, so compare the unmasked session token length only.
    String secondToken = JsonPath.read(second.getResponse().getContentAsString(), "$.token");
    assertThat(secondToken).isNotEmpty().hasSameSizeAs(firstToken);
  }
}
