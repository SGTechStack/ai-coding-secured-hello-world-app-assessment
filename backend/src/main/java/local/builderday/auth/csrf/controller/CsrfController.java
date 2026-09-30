package local.builderday.auth.csrf.controller;

import local.builderday.auth.csrf.controller.dto.CsrfResponse;
import org.springframework.http.MediaType;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exposes anonymous-session CSRF material through the response body only. */
@RestController
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
public class CsrfController {
  @GetMapping("/csrf")
  public CsrfResponse csrf(CsrfToken token) {
    return new CsrfResponse(token.getToken(), token.getHeaderName(), token.getParameterName());
  }
}
