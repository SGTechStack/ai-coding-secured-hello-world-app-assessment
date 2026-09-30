package local.builderday.auth.core.config;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** The configured default-deny matrix, observed through HTTP status codes. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class AuthorizationMatrixTest {
  @Autowired MockMvc mvc;

  @Test
  void should_openPublicVisitorRoutes_toAnonymousCallers() throws Exception {
    mvc.perform(get("/register").accept(MediaType.TEXT_HTML)).andExpect(status().isOk());
    mvc.perform(get("/login").accept(MediaType.TEXT_HTML)).andExpect(status().isOk());
    mvc.perform(get("/csrf")).andExpect(status().isOk());
  }

  @Test
  void should_refuseTheOldVersionedLoginPath_when_aVisitorPostsToIt() throws Exception {
    mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void should_reserveAdminApis_forTheAdminRole() throws Exception {
    mvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/admin/users").with(user("testuser123").roles("USER"))).andExpect(status().isForbidden());
    mvc.perform(get("/api/admin/users").with(user("testadmin1").roles("ADMIN"))).andExpect(status().isOk());
    // Account deletion (ADR 0014: the matrix, not method annotations, guards it).
    String someAccount = "/api/admin/users/" + UUID.randomUUID();
    mvc.perform(delete(someAccount).with(csrf())).andExpect(status().isUnauthorized());
    mvc.perform(delete(someAccount).with(csrf()).with(user("testuser123").roles("USER")))
        .andExpect(status().isForbidden());
  }

  @Test
  void should_grantTheGreeting_toUsersAndAdminsOnly_when_readWithGet() throws Exception {
    mvc.perform(get("/api/hello").with(user("testuser123").roles("USER"))).andExpect(status().isOk());
    mvc.perform(get("/api/hello").with(user("testadmin1").roles("ADMIN"))).andExpect(status().isOk());
    mvc.perform(get("/api/hello")).andExpect(status().isUnauthorized());
    mvc.perform(post("/api/hello").with(user("testuser123").roles("USER")).with(csrf()))
        .andExpect(status().isMethodNotAllowed());
  }

  @Test
  void should_openEveryFrontendPath_when_anyoneNavigatesToIt() throws Exception {
    mvc.perform(get("/").accept(MediaType.TEXT_HTML)).andExpect(status().isOk());
    mvc.perform(get("/").with(user("testuser123").roles("USER")).accept(MediaType.TEXT_HTML))
        .andExpect(status().isOk());
    mvc.perform(get("/account/settings").with(user("testuser123").roles("USER")).accept(MediaType.TEXT_HTML))
        .andExpect(status().isOk());
  }

  @Test
  void should_return401ForVisitorsAnd404ForUsers_when_anUnlistedApiPathIsCalled() throws Exception {
    mvc.perform(get("/api/anything")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/anything").with(user("testuser123").roles("USER"))).andExpect(status().isNotFound());
  }
}
