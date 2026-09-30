package org.eds.demo.common;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class WebSpaController {

  public static final String SITE_ROOT = WebPaths.ROOT;
  public static final String SPA_ROOT = "/app";
  public static final String SIGN_IN_PATH = "/welcome";

  /** SPA route holding the sign-in form; public, unlike the rest of {@code /app/**}. */
  public static final String SPA_SIGN_IN_PATH = SPA_ROOT + "/sign-in";

  @GetMapping(SITE_ROOT)
  public String redirectToApp(Authentication authentication) {
    if (authentication != null
        && authentication.isAuthenticated()
        && !(authentication instanceof AnonymousAuthenticationToken)) {
      return "redirect:" + SPA_ROOT + WebPaths.ROOT;
    }
    return "redirect:" + SIGN_IN_PATH;
  }

  @GetMapping(SIGN_IN_PATH)
  public String signIn() {
    return "forward:" + SIGN_IN_PATH + WebPaths.INDEX_HTML;
  }

  /**
   * SPA catch-all: forwards all /app/** routes to index.html, EXCEPT paths that have a file
   * extension in the last segment (e.g. .js, .css, .woff2). Those are static assets whose /app
   * prefix is stripped so the /** resource handler can resolve them directly from the static
   * location (e.g. /assets/main.js → {staticLocation}/assets/main.js).
   */
  @GetMapping({SPA_ROOT, SPA_ROOT + "/{*spaPath}"})
  public String spa(@PathVariable(required = false) String spaPath) {
    if (spaPath != null) {
      int lastSlash = spaPath.lastIndexOf('/');
      if (spaPath.indexOf('.', lastSlash + 1) >= 0) {
        // Has a file extension — strip /app and forward to the resource handler
        return "forward:" + spaPath;
      }
    }
    return "forward:" + WebPaths.INDEX_HTML;
  }
}
