package local.builderday.common.exception;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** ADR 0001: every JSON/REST failure path, MVC or Spring Security, returns the same Problem Details contract. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class ProblemDetailsContractTest {
  @Autowired MockMvc mvc;

  @Test
  void should_returnAuthenticationRequired_when_anonymousCallsAnAuthenticatedApi() throws Exception {
    expectProblem(get("/api/anything"), ApiError.AUTHENTICATION_REQUIRED);
  }

  @Test
  void should_returnResourceNotFound_when_authenticatedUserCallsAnUnknownApi() throws Exception {
    // A denied but existing endpoint answers ACCESS_DENIED instead; RoutingTest covers it with a fixture endpoint.
    expectProblem(get("/api/anything").with(user("test")), ApiError.RESOURCE_NOT_FOUND);
  }

  @Test
  void should_returnCsrfTokenRejected_when_csrfTokenIsMissing() throws Exception {
    expectProblem(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
        .content("{\"username\":\"nobody1\",\"password\":\"whatever\"}"), ApiError.CSRF_TOKEN_REJECTED);
  }

  @Test
  void should_returnInvalidCredentials_when_loginFails() throws Exception {
    expectProblem(login("nobody1"), ApiError.INVALID_CREDENTIALS);
  }

  @Test
  void should_returnInvalidRequest_when_bodyIsMalformedJson() throws Exception {
    expectProblem(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{"),
        ApiError.INVALID_REQUEST);
  }

  @Test
  void should_returnUnsupportedMediaType_when_contentTypeIsNotJson() throws Exception {
    expectProblem(post("/api/auth/login").with(csrf()).contentType(MediaType.TEXT_PLAIN).content("x"),
        ApiError.UNSUPPORTED_MEDIA_TYPE);
  }

  @Test
  void should_returnMethodNotAllowed_when_aFrontendPathIsPosted() throws Exception {
    expectProblem(post("/home").with(csrf()), ApiError.METHOD_NOT_ALLOWED);
  }

  private void expectProblem(RequestBuilder request, ApiError error) throws Exception {
    mvc.perform(request)
        .andExpect(status().is(error.status().value()))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(error.status().value()))
        .andExpect(jsonPath("$.code").value(error.name()))
        .andExpect(jsonPath("$.detail").value(error.detail()));
  }

  private static MockHttpServletRequestBuilder login(String username) {
    return post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
        .content("{\"username\":\"" + username + "\",\"password\":\"Wrong-Password1\"}");
  }
}
