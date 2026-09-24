package com.example.demo_app.hello;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo_app.auth.SpaAuthFlow;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** The PRD's protected-content check: {@code GET /api/v1/hello} over the real filter chain. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HelloApiTest {

  @Autowired private MockMvc mvc;

  @Test
  void signedInUserIsGreetedByUsernameAsJson() throws Exception {
    mvc.perform(get("/api/v1/hello").session(SpaAuthFlow.logIn(mvc)))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(content().json("{\"message\": \"Hello, johndoe\"}", true));
  }

  @Test
  void browserStyleAcceptHeaderStillGetsJson() throws Exception {
    mvc.perform(
            get("/api/v1/hello")
                .session(SpaAuthFlow.logIn(mvc))
                .accept(MediaType.TEXT_HTML, MediaType.ALL))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
  }

  @Test
  void anonymousRequestGets401Json() throws Exception {
    mvc.perform(get("/api/v1/hello"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }
}
