package org.eds.demo;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.io.IOException;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The {@code local} profile keeps its data in a file-based H2 database, which Spring Boot does not
 * treat as embedded, so it will not create the session tables on its own. Signing in must still
 * work there.
 */
@ActiveProfiles("local")
@AutoConfigureMockMvc
@SpringBootTest(
    properties = {
      "app.email.inbound.enabled=false",
      "app.admin.username=file-db-admin",
      "app.admin.password=test-only-file-db-secret"
    })
class LocalFileDatabaseSessionIT {

  @DynamicPropertySource
  static void fileDatabase(DynamicPropertyRegistry registry) throws IOException {
    var dir = Files.createTempDirectory("local-file-db");
    registry.add("spring.datasource.url", () -> "jdbc:h2:file:" + dir + "/testdb");
  }

  @Autowired private MockMvc mockMvc;

  @Test
  void signInWorksOnTheFileBackedDatabase() throws Exception {
    mockMvc
        .perform(
            post("/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"username\":\"file-db-admin\",\"password\":\"test-only-file-db-secret\"}")
                .cookie(new Cookie("XSRF-TOKEN", "test-csrf-token"))
                .header("X-XSRF-TOKEN", "test-csrf-token"))
        .andExpect(status().isOk());
  }
}
