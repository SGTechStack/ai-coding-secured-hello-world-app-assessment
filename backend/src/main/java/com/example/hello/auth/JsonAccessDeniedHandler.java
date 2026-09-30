package com.example.hello.auth;

import com.example.hello.common.ProblemJson;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;

/** 403 as JSON; distinguishes a missing/invalid CSRF token so the SPA can refresh it. */
@Component
public class JsonAccessDeniedHandler implements AccessDeniedHandler {

  public static final String CSRF_DETAIL = "CSRF token missing or invalid";

  @Override
  public void handle(
      HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception)
      throws IOException {
    String detail = exception instanceof CsrfException ? CSRF_DETAIL : "Access denied";
    ProblemJson.write(response, HttpStatus.FORBIDDEN, detail);
  }
}
