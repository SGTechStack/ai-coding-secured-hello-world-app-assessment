package local.builderday.auth.core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class SecurityConfigTest {
  private static final Pattern SCRIPT_SOURCE = Pattern.compile("src=\"(/assets/[^\"]+\\.js)\"");
  private static final Pattern DOCUMENT_NONCE = Pattern.compile("property=\"csp-nonce\" nonce=\"([^\"]+)\"");

  @Autowired MockMvc mvc;

  @Test
  void should_serveGeneratedAssetDirectly_whenDocumentReferencesIt() throws Exception {
    String document = mvc.perform(get("/login").accept(MediaType.TEXT_HTML)).andReturn().getResponse()
        .getContentAsString();
    var source = SCRIPT_SOURCE.matcher(document);
    assertThat(source.find()).isTrue();

    mvc.perform(get(source.group(1)))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith("text/javascript"))
        .andExpect(content().string(not(startsWith("<!doctype html>"))));
  }

  // Which paths get the SPA document, a 404 or a denial is covered path by path in RoutingTest.

  /** Spring Security's default, pinned so an upgrade or a headers() change cannot weaken it unnoticed. No preload. */
  @Test
  void should_sendHstsForOneYearIncludingSubdomains_when_requestIsSecure() throws Exception {
    for (String path : new String[] {"/csrf", "/login", "/actuator/health"}) {
      mvc.perform(get(path).secure(true))
          .andExpect(header().string("Strict-Transport-Security", "max-age=31536000 ; includeSubDomains"));
    }
  }

  @Test
  void should_serveUniqueStrictNonceAndNoStore_whenSpaDocumentRequested() throws Exception {
    MvcResult login = requestDocument("/login");
    MvcResult root = requestDocument("/", true);
    MvcResult deepLink = requestDocument("/index.html", true);
    String loginNonce = nonceFrom(login);
    String rootNonce = nonceFrom(root);
    String deepLinkNonce = nonceFrom(deepLink);

    assertThat(loginNonce).isNotEqualTo(rootNonce).isNotEqualTo(deepLinkNonce);
    assertThat(root.getResponse().getHeader("Content-Security-Policy"))
        .contains("default-src 'self'", "script-src 'self' 'nonce-" + rootNonce + "'",
            "style-src 'self' 'nonce-" + rootNonce + "'", "style-src-attr 'none'")
        .doesNotContain("unsafe-inline");
    assertThat(root.getResponse().getContentAsString())
        .doesNotContain("__CSP_NONCE__")
        .contains("nonce=\"" + rootNonce + "\"");
  }

  private MvcResult requestDocument(String path) throws Exception {
    return requestDocument(path, false);
  }

  private MvcResult requestDocument(String path, boolean authenticated) throws Exception {
    var request = get(path).accept(MediaType.TEXT_HTML);
    if (authenticated) {
      request.with(user("test"));
    }
    MvcResult result = mvc.perform(request)
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(content().contentType("text/html;charset=UTF-8"))
        .andReturn();
    assertThat(result.getResponse().getCharacterEncoding()).isEqualTo(StandardCharsets.UTF_8.name());
    return result;
  }

  private String nonceFrom(MvcResult result) throws Exception {
    var nonce = DOCUMENT_NONCE.matcher(result.getResponse().getContentAsString());
    assertThat(nonce.find()).isTrue();
    return nonce.group(1);
  }
}
