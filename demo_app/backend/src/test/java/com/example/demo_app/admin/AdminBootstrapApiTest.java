package com.example.demo_app.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo_app.auth.SpaAuthFlow;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The {@code test} profile's bootstrap admin, created at startup from {@code app.admin.*}, is a
 * working {@code ADMIN} account. {@code AdminBootstrapStartupTest} covers when it is created.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminBootstrapApiTest {

  @Autowired private MockMvc mvc;

  @Test
  void bootstrapAdminCanLogInAndIsAnAdmin() throws Exception {
    mvc.perform(get("/api/v1/auth/me").session(SpaAuthFlow.logInAsAdmin(mvc)))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json("{\"username\": \"admin\", \"firstName\": \"Admin\", \"role\": \"ADMIN\"}"));
  }
}
